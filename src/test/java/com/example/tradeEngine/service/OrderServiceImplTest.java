package com.example.tradeEngine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.tradeEngine.common.CacheDistributeLock;
import com.example.tradeEngine.common.DataStorage;
import com.example.tradeEngine.common.DistributedLockKey;
import com.example.tradeEngine.common.OrderStatus;
import com.example.tradeEngine.common.OrderType;
import com.example.tradeEngine.common.PriceType;
import com.example.tradeEngine.pojo.OrderPojo;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

	@InjectMocks
	private OrderServiceImpl orderService;

	@Mock
	private OrderDao orderDao;

	@Mock
	private CacheDistributeLock cacheDistributeLock;

	@Spy
	private DataStorage dataStorage = new DataStorage();

	@BeforeEach
	public void setUp() {
		// Clear caches before each test
		dataStorage.BUY_ORDER_BOOK_CACHE.clear();
		dataStorage.SELL_ORDER_BOOK_CACHE.clear();
		dataStorage.PROCESSING_TASK_QUEUE.clear();
		dataStorage.FILLED_TASK_QUEUE.clear();
	}

	@Test
	public void testInsertLimitOrderSuccessfully() {
		// Arrange
		OrderPojo order = new OrderPojo();
		order.setId(UUID.randomUUID());
		order.setOrderType(OrderType.BUY);
		order.setPriceType(PriceType.LIMIT);
		order.setPrice(new BigDecimal("10.00"));
		order.setQuantity(100L);

		when(cacheDistributeLock.lock(eq(DistributedLockKey.ORDER_BOOK_CACHE.name()), anyLong())).thenReturn(true);
		when(orderDao.insert(any(OrderPojo.class))).thenReturn(order);

		// Act
		orderService.insert(order);

		// Assert
		// Verify lock/unlock sequence
		verify(cacheDistributeLock).lock(eq(DistributedLockKey.ORDER_BOOK_CACHE.name()), anyLong());
		verify(cacheDistributeLock).unlock(eq(DistributedLockKey.ORDER_BOOK_CACHE.name()));

		// Verify order is added to correct caches
		ConcurrentSkipListSet<OrderPojo> queue = dataStorage.BUY_ORDER_BOOK_CACHE.get(order.getPriceKey());
		assertNotNull(queue);
		assertTrue(queue.contains(order));
		assertTrue(dataStorage.PROCESSING_TASK_QUEUE.contains(order));
	}

	@Test
	public void testProcessMatchEngineLimitOrderMatchingExactPriceOnly() throws Exception {
		// Arrange
		// 1. Setup target SELL queue with different price levels
		BigDecimal matchPrice = new BigDecimal("10.00");
		BigDecimal nonMatchPrice = new BigDecimal("10.05");

		OrderPojo targetOrder1 = new OrderPojo(UUID.randomUUID(), LocalDateTime.now().minusMinutes(5),
				LocalDateTime.now(),
				OrderType.SELL, PriceType.LIMIT, matchPrice, 6L, OrderStatus.PROCESSING, new ArrayList<>());
		OrderPojo targetOrder2 = new OrderPojo(UUID.randomUUID(), LocalDateTime.now().minusMinutes(2),
				LocalDateTime.now(),
				OrderType.SELL, PriceType.LIMIT, nonMatchPrice, 4L, OrderStatus.PROCESSING, new ArrayList<>());

		ConcurrentSkipListSet<OrderPojo> matchQueue = new ConcurrentSkipListSet<>();
		matchQueue.add(targetOrder1);
		dataStorage.SELL_ORDER_BOOK_CACHE.put(matchPrice.setScale(2), matchQueue);

		ConcurrentSkipListSet<OrderPojo> nonMatchQueue = new ConcurrentSkipListSet<>();
		nonMatchQueue.add(targetOrder2);
		dataStorage.SELL_ORDER_BOOK_CACHE.put(nonMatchPrice.setScale(2), nonMatchQueue);

		// 2. Incoming BUY Limit order matching only price 10.00
		OrderPojo incomingOrder = new OrderPojo(UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now(),
				OrderType.BUY, PriceType.LIMIT, matchPrice, 10L, OrderStatus.PROCESSING, new ArrayList<>());

		// Add it to incoming currentQueue so it exists in queue
		ConcurrentSkipListSet<OrderPojo> incomingQueue = new ConcurrentSkipListSet<>();
		incomingQueue.add(incomingOrder);
		dataStorage.BUY_ORDER_BOOK_CACHE.put(matchPrice.setScale(2), incomingQueue);

		when(cacheDistributeLock.lock(eq(DistributedLockKey.ORDER_BOOK_CACHE.name()), anyLong())).thenReturn(true);
		when(orderDao.findById(targetOrder1.getId())).thenReturn(targetOrder1);
		when(orderDao.findById(incomingOrder.getId())).thenReturn(incomingOrder);

		// Act
		OrderPojo result = orderService.processMatchEngine(incomingOrder);

		// Assert
		// Incoming order matched 6 units of targetOrder1 (at price 10.00)
		assertEquals(4L, result.getQuantity());
		assertEquals(OrderStatus.PROCESSING, result.getOrderStatus());
		assertEquals(1, result.getMatchOrderDetailPojo().size());
		assertEquals(targetOrder1.getId(), result.getMatchOrderDetailPojo().get(0).getId());
		assertEquals(6L, result.getMatchOrderDetailPojo().get(0).getQuantity());

		// TargetOrder1 at price 10.00 should be fully filled and removed from cache
		assertEquals(0L, targetOrder1.getQuantity());
		assertEquals(OrderStatus.FILLED, targetOrder1.getOrderStatus());
		assertTrue(dataStorage.FILLED_TASK_QUEUE.contains(targetOrder1));
		assertFalse(dataStorage.SELL_ORDER_BOOK_CACHE.containsKey(matchPrice.setScale(2)));

		// TargetOrder2 at price 10.05 should remain untouched
		assertEquals(4L, targetOrder2.getQuantity());
		assertEquals(OrderStatus.PROCESSING, targetOrder2.getOrderStatus());
		assertTrue(dataStorage.SELL_ORDER_BOOK_CACHE.get(nonMatchPrice.setScale(2)).contains(targetOrder2));

		// Incoming order still has remaining qty, so it should be added back to BUY
		// book
		assertTrue(dataStorage.BUY_ORDER_BOOK_CACHE.get(matchPrice.setScale(2)).contains(result));
	}

	@Test
	public void testProcessMatchEngineMarketOrderMatchesAcrossMultiplePrices() throws Exception {
		// Arrange
		// 1. Setup target SELL queue with different price levels
		BigDecimal bestPrice = new BigDecimal("10.00");
		BigDecimal secondBestPrice = new BigDecimal("10.05");

		// Oldest order at price 10.00
		OrderPojo targetOrder1 = new OrderPojo(UUID.randomUUID(), LocalDateTime.now().minusMinutes(10),
				LocalDateTime.now(),
				OrderType.SELL, PriceType.LIMIT, bestPrice, 5L, OrderStatus.PROCESSING, new ArrayList<>());
		// Newest order at price 10.00
		OrderPojo targetOrder2 = new OrderPojo(UUID.randomUUID(), LocalDateTime.now().minusMinutes(5),
				LocalDateTime.now(),
				OrderType.SELL, PriceType.LIMIT, bestPrice, 5L, OrderStatus.PROCESSING, new ArrayList<>());
		// Order at price 10.05
		OrderPojo targetOrder3 = new OrderPojo(UUID.randomUUID(), LocalDateTime.now().minusMinutes(2),
				LocalDateTime.now(),
				OrderType.SELL, PriceType.LIMIT, secondBestPrice, 10L, OrderStatus.PROCESSING, new ArrayList<>());

		ConcurrentSkipListSet<OrderPojo> queueAt10 = new ConcurrentSkipListSet<>();
		queueAt10.add(targetOrder1);
		queueAt10.add(targetOrder2);
		dataStorage.SELL_ORDER_BOOK_CACHE.put(bestPrice.setScale(2), queueAt10);

		ConcurrentSkipListSet<OrderPojo> queueAt10_05 = new ConcurrentSkipListSet<>();
		queueAt10_05.add(targetOrder3);
		dataStorage.SELL_ORDER_BOOK_CACHE.put(secondBestPrice.setScale(2), queueAt10_05);

		// 2. Incoming BUY Market order (no price, MARKET type) requesting quantity of
		// 15
		OrderPojo incomingOrder = new OrderPojo(UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now(),
				OrderType.BUY, PriceType.MARKET, null, 15L, OrderStatus.PROCESSING, new ArrayList<>());

		when(cacheDistributeLock.lock(eq(DistributedLockKey.ORDER_BOOK_CACHE.name()), anyLong())).thenReturn(true);
		when(orderDao.findById(incomingOrder.getId())).thenReturn(incomingOrder);
		when(orderDao.findById(targetOrder1.getId())).thenReturn(targetOrder1);
		when(orderDao.findById(targetOrder2.getId())).thenReturn(targetOrder2);
		when(orderDao.findById(targetOrder3.getId())).thenReturn(targetOrder3);

		// Act
		OrderPojo result = orderService.processMatchEngine(incomingOrder);

		// Assert
		// Market order is fully filled (quantity matches targetOrder1, targetOrder2,
		// and 5 of targetOrder3)
		assertEquals(0L, result.getQuantity());
		assertEquals(OrderStatus.FILLED, result.getOrderStatus());
		assertEquals(3, result.getMatchOrderDetailPojo().size());

		// Match order details check
		assertEquals(targetOrder1.getId(), result.getMatchOrderDetailPojo().get(0).getId());
		assertEquals(5L, result.getMatchOrderDetailPojo().get(0).getQuantity());
		assertEquals(targetOrder2.getId(), result.getMatchOrderDetailPojo().get(1).getId());
		assertEquals(5L, result.getMatchOrderDetailPojo().get(1).getQuantity());
		assertEquals(targetOrder3.getId(), result.getMatchOrderDetailPojo().get(2).getId());
		assertEquals(5L, result.getMatchOrderDetailPojo().get(2).getQuantity());

		// targetOrder1 and targetOrder2 should be FILLED, targetOrder3 should be
		// partially filled (remaining 5)
		assertEquals(OrderStatus.FILLED, targetOrder1.getOrderStatus());
		assertEquals(OrderStatus.FILLED, targetOrder2.getOrderStatus());
		assertEquals(OrderStatus.PROCESSING, targetOrder3.getOrderStatus());
		assertEquals(5L, targetOrder3.getQuantity());

		// Verify filled queue and cache removal
		assertTrue(dataStorage.FILLED_TASK_QUEUE.contains(targetOrder1));
		assertTrue(dataStorage.FILLED_TASK_QUEUE.contains(targetOrder2));
		assertTrue(dataStorage.FILLED_TASK_QUEUE.contains(result));
		assertFalse(dataStorage.SELL_ORDER_BOOK_CACHE.containsKey(bestPrice.setScale(2))); // Entire 10.00 level cleared
		assertTrue(dataStorage.SELL_ORDER_BOOK_CACHE.get(secondBestPrice.setScale(2)).contains(targetOrder3));
	}

	@Test
	public void testProcessMatchEngineMarketOrderWithNoMatches() throws Exception {
		// Arrange: Sell book is completely empty
		OrderPojo incomingOrder = new OrderPojo(UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now(),
				OrderType.BUY, PriceType.MARKET, null, 15L, OrderStatus.PROCESSING, new ArrayList<>());

		when(cacheDistributeLock.lock(eq(DistributedLockKey.ORDER_BOOK_CACHE.name()), anyLong())).thenReturn(true);

		// Act
		OrderPojo result = orderService.processMatchEngine(incomingOrder);

		// Assert: Order status remains PROCESSING, quantity remains 15, and is NOT
		// double-queued in matching engine
		assertEquals(15L, result.getQuantity());
		assertEquals(OrderStatus.PROCESSING, result.getOrderStatus());
		assertTrue(result.getMatchOrderDetailPojo().isEmpty());
		assertTrue(dataStorage.FILLED_TASK_QUEUE.isEmpty());
	}
}
