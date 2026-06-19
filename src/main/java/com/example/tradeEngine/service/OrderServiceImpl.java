package com.example.tradeEngine.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.tradeEngine.common.CacheDistributeLock;
import com.example.tradeEngine.common.DataStorage;
import com.example.tradeEngine.common.DistributedLockKey;
import com.example.tradeEngine.common.OrderStatus;
import com.example.tradeEngine.common.OrderType;
import com.example.tradeEngine.common.PriceType;
import com.example.tradeEngine.pojo.OrderDetailPojo;
import com.example.tradeEngine.pojo.OrderPojo;

@Service
public class OrderServiceImpl implements OrderService {
	private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

	@Autowired
	private OrderDao orderDao;
	@Autowired
	private DataStorage dataStorage;

	@Autowired
	private CacheDistributeLock cacheDistributeLock;

	@Override
	public OrderPojo processMatchEngine(OrderPojo orderPojo) throws Exception {
		boolean isLocked = false;
		try {
			if (cacheDistributeLock.lock(DistributedLockKey.ORDER_BOOK_CACHE.name(), 3)) {
				isLocked = true;

				ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<OrderPojo>> currentQueue = OrderType.BUY
						.equals(orderPojo.getOrderType()) ? dataStorage.BUY_ORDER_BOOK_CACHE
								: dataStorage.SELL_ORDER_BOOK_CACHE;
				ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<OrderPojo>> targetQueue = OrderType.BUY
						.equals(orderPojo.getOrderType()) ? dataStorage.SELL_ORDER_BOOK_CACHE
								: dataStorage.BUY_ORDER_BOOK_CACHE;

				UUID orderID = orderPojo.getId();
				long[] accumulatedSum = { 0L };
				long requiredQuantity = orderPojo.getQuantity();
				List<OrderPojo> matchedList = null;

				if (null != orderPojo.getPriceKey() && PriceType.LIMIT.equals(orderPojo.getPriceType())) {
					BigDecimal priceKey = orderPojo.getPriceKey();
					ConcurrentSkipListSet<OrderPojo> targetPriceQueue = targetQueue.get(priceKey);

					// Get match orders by price priority, and time priority.
					matchedList = Optional.ofNullable(targetPriceQueue)
							.map(queue -> queue.stream()
									.takeWhile(order -> {
										boolean keepGoing = accumulatedSum[0] < requiredQuantity;
										accumulatedSum[0] += order.getQuantity();
										return keepGoing;
									})
									.collect(Collectors.toList()))
							.orElse(Collections.emptyList());
				} else {
					// Get all order in target queue by price priority, and time priority.
					matchedList = Optional.ofNullable(targetQueue)
							.map(map -> map.values().stream()
									.filter(java.util.Objects::nonNull)
									.flatMap(java.util.Collection::stream).takeWhile(order -> {
										boolean keepGoing = accumulatedSum[0] < requiredQuantity;
										accumulatedSum[0] += order.getQuantity();
										return keepGoing;
									})
									.collect(Collectors.toList()))
							.orElse(java.util.Collections.emptyList());
				}

				if (null != matchedList && matchedList.size() > 0) {
					long remainingQuantity = orderPojo.getQuantity();
					LocalDateTime currentTime = LocalDateTime.now();
					for (OrderPojo targetOrderPojo : matchedList) {
						if (remainingQuantity <= 0) {
							break;
						}

						long matchQuantity = Math.min(remainingQuantity, targetOrderPojo.getQuantity());
						remainingQuantity -= matchQuantity;

						// Current macth detail use Maker's price, and Taker's ID.
						OrderDetailPojo targetOrderDetailPojo = new OrderDetailPojo();
						targetOrderDetailPojo.setId(targetOrderPojo.getId());
						targetOrderDetailPojo.setPrice(targetOrderPojo.getPrice());
						targetOrderDetailPojo.setQuantity(matchQuantity);
						targetOrderDetailPojo.setUpdateTime(currentTime);
						orderPojo.getMatchOrderDetailPojo().add(targetOrderDetailPojo);

						// Match detail record by Taker's ID, and Maker's price.
						OrderDetailPojo currenDetailPojo = new OrderDetailPojo();
						currenDetailPojo.setId(orderID);
						currenDetailPojo.setPrice(targetOrderPojo.getPrice());
						currenDetailPojo.setQuantity(matchQuantity);
						currenDetailPojo.setUpdateTime(currentTime);
						targetOrderPojo.getMatchOrderDetailPojo().add(currenDetailPojo);

						// Update target order's quantity and status
						long newTargetQty = targetOrderPojo.getQuantity() - matchQuantity;
						targetOrderPojo.setQuantity(newTargetQty);
						targetOrderPojo.setOrderStatus(newTargetQty == 0 ? OrderStatus.FILLED : OrderStatus.PROCESSING);
						targetOrderPojo.setUpdateTime(currentTime);

						update(targetOrderPojo);

						// Target order is filled, remove from cache and add to filled queue.
						if (newTargetQty <= 0) {
							BigDecimal targetPriceKey = targetOrderPojo.getPriceKey();
							ConcurrentSkipListSet<OrderPojo> queueAtPrice = targetQueue.get(targetPriceKey);
							if (queueAtPrice != null) {
								queueAtPrice.remove(targetOrderPojo);
								if (queueAtPrice.isEmpty()) {
									targetQueue.remove(targetPriceKey);
								}
							}
							dataStorage.FILLED_TASK_QUEUE.add(targetOrderPojo);
						}
					}

					// Update current order's quantity and status
					orderPojo.setQuantity(remainingQuantity);
					orderPojo.setOrderStatus(remainingQuantity == 0 ? OrderStatus.FILLED : OrderStatus.PROCESSING);
					orderPojo.setUpdateTime(currentTime);

					// If current order is partially filled and limit order, add to cache
					if (OrderStatus.PROCESSING.equals(orderPojo.getOrderStatus())
							&& PriceType.LIMIT.equals(orderPojo.getPriceType())) {
						BigDecimal priceKey = orderPojo.getPriceKey();
						if (currentQueue.containsKey(priceKey)) {
							currentQueue.get(priceKey).add(orderPojo);
						} else {
							ConcurrentSkipListSet<OrderPojo> orderQueue = new ConcurrentSkipListSet<>();
							orderQueue.add(orderPojo);
							currentQueue.put(priceKey, orderQueue);
						}
					}

					// Current order is filled, add to filled queue.
					if (remainingQuantity == 0) {
						dataStorage.FILLED_TASK_QUEUE.add(orderPojo);
					}

					update(orderPojo);
				}
			} else {
				LOGGER.warn("Failed to get a lock during matching, KEY : {}",
						DistributedLockKey.ORDER_BOOK_CACHE.name());
				throw new RuntimeException("System busy, matching delayed");
			}
		} finally {
			if (isLocked) {
				cacheDistributeLock.unlock(DistributedLockKey.ORDER_BOOK_CACHE.name());
			}
		}
		return orderPojo;
	}

	@Override
	public void insert(OrderPojo orderPojo) {

		boolean isLocked = false;
		try {
			if (cacheDistributeLock.lock(DistributedLockKey.ORDER_BOOK_CACHE.name(), 3)) {
				isLocked = true;
				orderPojo.setCreateTime(LocalDateTime.now());
				orderPojo.setUpdateTime(LocalDateTime.now());
				orderPojo = orderDao.insert(orderPojo);

				if (null != orderPojo.getPriceKey() && PriceType.LIMIT.equals(orderPojo.getPriceType())) {
					ConcurrentSkipListSet<OrderPojo> orderQueue = null;
					if (OrderType.BUY.equals(orderPojo.getOrderType())) {
						orderQueue = dataStorage.BUY_ORDER_BOOK_CACHE.get(orderPojo.getPriceKey());
					} else {
						orderQueue = dataStorage.SELL_ORDER_BOOK_CACHE.get(orderPojo.getPriceKey());
					}

					if (null == orderQueue) {
						orderQueue = new ConcurrentSkipListSet<OrderPojo>();
					}
					orderQueue.add(orderPojo);

					if (OrderType.BUY.equals(orderPojo.getOrderType())) {
						dataStorage.BUY_ORDER_BOOK_CACHE.put(orderPojo.getPriceKey(), orderQueue);
					} else {
						dataStorage.SELL_ORDER_BOOK_CACHE.put(orderPojo.getPriceKey(), orderQueue);
					}
				}
				dataStorage.PROCESSING_TASK_QUEUE.add(orderPojo);
			} else {
				LOGGER.warn("Faild to get a lock, KEY : {}", DistributedLockKey.ORDER_BOOK_CACHE.name());
				throw new RuntimeException("System busy, please try again later");
			}
		} finally {
			if (isLocked) {
				cacheDistributeLock.unlock(DistributedLockKey.ORDER_BOOK_CACHE.name());
			}
		}

	}

	@Override
	public void update(OrderPojo orderPojo) {
		OrderPojo orderEntity = orderDao.findById(orderPojo.getId());
		orderEntity.setMatchOrderDetailPojo(orderPojo.getMatchOrderDetailPojo());
		orderEntity.setUpdateTime(orderPojo.getUpdateTime());
		orderEntity.setOrderStatus(orderPojo.getOrderStatus());
		orderDao.update(orderEntity);
	}
}
