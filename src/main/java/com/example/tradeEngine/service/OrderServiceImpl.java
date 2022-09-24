package com.example.tradeEngine.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
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

	@Override
	public OrderPojo processMatchEngine(OrderPojo orderPojo) throws Exception{
		String targetPrice = orderPojo.getPriceKey();
		
		CacheDistributeLock orderBookLock = new CacheDistributeLock(dataStorage.DISTRIBUTED_LOCK, DistributedLockKey.ORDER_BOOK_CACHE.name(), null);
		if(null != orderBookLock.lock()) {
			boolean orderExistInQueue = false;
			HashMap<String, TreeSet<OrderPojo>> currentQueue = OrderType.BUY.equals(orderPojo.getOrderType()) ? dataStorage.BUY_ORDER_BOOK_CACHE : dataStorage.SELL_ORDER_BOOK_CACHE; 
			HashMap<String, TreeSet<OrderPojo>> targetQueue = OrderType.BUY.equals(orderPojo.getOrderType()) ? dataStorage.SELL_ORDER_BOOK_CACHE : dataStorage.BUY_ORDER_BOOK_CACHE; 
			
			final UUID orderID = orderPojo.getId();
			
			if (null != orderPojo.getPriceKey()) {
				// Check current task still in processing
				if(null != currentQueue.get(orderPojo.getPriceKey())) {
					orderExistInQueue = currentQueue.get(orderPojo.getPriceKey()).stream()
							.filter(order -> order.getId().equals(orderID)).findFirst().isPresent();
				}
			} else {
				if (PriceType.MARKET.equals(orderPojo.getPriceType())) {
					orderExistInQueue = true;
				}
			}
			
			if(orderExistInQueue) {
				if(PriceType.MARKET.equals(orderPojo.getPriceType())) {
					// Get max/sell price
					targetPrice = getSortPrice(!OrderType.BUY.equals(orderPojo.getOrderType()), targetQueue);
					
					if(null != orderPojo.getPriceKey() && orderPojo.getPriceKey() != targetPrice) {
						// Remove market order from queue when current max/min price is change.
						currentQueue.get(orderPojo.getPriceKey()).removeIf(order -> order.getId().equals(orderID));
					}
					
					if(null != targetPrice ) {
						orderPojo.setPrice(new BigDecimal(targetPrice));
						if(currentQueue.containsKey(targetPrice)) {
							currentQueue.get(targetPrice).add(orderPojo);
						}else {
							TreeSet<OrderPojo> orderQueue = new TreeSet<OrderPojo>();
							orderQueue.add(orderPojo);
							currentQueue.put(targetPrice, orderQueue);
						}
					}else {
						orderPojo.setPrice(null);
					}
				}
				
				if(null != orderPojo.getPriceKey()) {
					OrderPojo currentCacheOrder = currentQueue.get(orderPojo.getPriceKey()).first();
					if(currentCacheOrder.getId() == orderPojo.getId()) {
						//Remove current order from queue
						currentCacheOrder = currentQueue.get(orderPojo.getPriceKey()).pollFirst();
						LOGGER.info(String.format("Start match, Price: %s, Quantity: %d, Price Type: %s, Target type: %s", targetPrice, orderPojo.getQuantity(), orderPojo.getPriceType(), orderPojo.getOrderType()));
						
						TreeSet<OrderPojo> targetPriceQueue = targetQueue.get(orderPojo.getPriceKey()); 
						TreeSet<OrderPojo> tempTargetQueue = new TreeSet<OrderPojo>();
						
						boolean hasMatched = false;
						
						while (currentCacheOrder.getQuantity() > 0L && null!= targetPriceQueue && targetPriceQueue.size() > 0) {
							hasMatched = true;
							OrderPojo targetOrder = targetPriceQueue.pollFirst();
							LocalDateTime currentTime = LocalDateTime.now();
							if(currentCacheOrder.getQuantity() - targetOrder.getQuantity() >= 0L) {
								currentCacheOrder.setQuantity(currentCacheOrder.getQuantity() - targetOrder.getQuantity());
								currentCacheOrder.setOrderStatus(currentCacheOrder.getQuantity() == 0 ? OrderStatus.FILLED : OrderStatus.PROCESSING);
								currentCacheOrder.setUpdateTime(currentTime);
								currentCacheOrder.getMatchOrderDetailPojo().add(new OrderDetailPojo(targetOrder.getId(), currentTime, targetOrder.getPrice(), targetOrder.getQuantity()));
								
								targetOrder.setOrderStatus(OrderStatus.FILLED);
								targetOrder.setUpdateTime(currentTime);
								targetOrder.getMatchOrderDetailPojo().add(new OrderDetailPojo(currentCacheOrder.getId(), currentTime, currentCacheOrder.getPrice(), targetOrder.getQuantity()));
								targetOrder.setQuantity(0L);
								
								dataStorage.FILLED_TASK_QUEUE.add(targetOrder);
							}else {
								targetOrder.setQuantity(targetOrder.getQuantity() - currentCacheOrder.getQuantity());
								targetOrder.getMatchOrderDetailPojo().add(new OrderDetailPojo(currentCacheOrder.getId(), currentTime, currentCacheOrder.getPrice(), currentCacheOrder.getQuantity()));
								
								
								currentCacheOrder.setOrderStatus(OrderStatus.FILLED);
								currentCacheOrder.getMatchOrderDetailPojo().add(new OrderDetailPojo(targetOrder.getId(), currentTime, targetOrder.getPrice(), currentCacheOrder.getQuantity()));
								currentCacheOrder.setQuantity(0L);
								currentCacheOrder.setUpdateTime(currentTime);
								
							}
							if(OrderStatus.FILLED.equals(currentCacheOrder.getOrderStatus())) {
								dataStorage.FILLED_TASK_QUEUE.add(currentCacheOrder);
							}
							
							if(OrderStatus.PROCESSING.equals(targetOrder.getOrderStatus())) {
								tempTargetQueue.add(targetOrder);
							}
						}
						
						if(OrderStatus.PROCESSING.equals(currentCacheOrder.getOrderStatus()) && PriceType.LIMIT.equals(currentCacheOrder.getPriceType())) {
							currentQueue.get(orderPojo.getPriceKey()).add(currentCacheOrder);
						}
						
						if(hasMatched) {
							if(null != targetPriceQueue && targetPriceQueue.size() > 0) {
								tempTargetQueue.addAll(targetPriceQueue);
							}
							if(tempTargetQueue.size() > 0) {
								targetQueue.put(currentCacheOrder.getPriceKey(), tempTargetQueue);
							}else {
								targetQueue.remove(currentCacheOrder.getPriceKey());
							}
						}else {
							if(null != targetPriceQueue && targetPriceQueue.size() > 0) {
								targetQueue.put(currentCacheOrder.getPriceKey(), targetPriceQueue);
							}else {
								targetQueue.remove(currentCacheOrder.getPriceKey());
							}
						}
						
						if(currentQueue.get(currentCacheOrder.getPriceKey()).size() <= 0) {
							currentQueue.remove(currentCacheOrder.getPriceKey());
						}
						
						dataStorage.BUY_ORDER_BOOK_CACHE = OrderType.BUY.equals(currentCacheOrder.getOrderType()) ? currentQueue : targetQueue;
						dataStorage.SELL_ORDER_BOOK_CACHE = OrderType.BUY.equals(currentCacheOrder.getOrderType()) ? targetQueue : currentQueue;
						
						orderPojo = (OrderPojo)currentCacheOrder.clone();
					}
				}
			}else {
				orderPojo.setOrderStatus(OrderStatus.FILLED);
			}
			orderBookLock.unlock();
		}
		return orderPojo;
	}

	@Override
	public void insert(OrderPojo orderPojo) {
		orderPojo = orderDao.insert(orderPojo);
		CacheDistributeLock orderBookLock = new CacheDistributeLock(dataStorage.DISTRIBUTED_LOCK, DistributedLockKey.ORDER_BOOK_CACHE.name(), null);
		if(null != orderBookLock.lock()) {
			if(PriceType.MARKET.equals(orderPojo.getPriceType())) {
				String priceString = null;
				
				if(OrderType.BUY.equals(orderPojo.getOrderType())){
					// Get min sell price
					priceString = getSortPrice(false, dataStorage.SELL_ORDER_BOOK_CACHE);
				}else {
					// Get max buy price
					priceString = getSortPrice(true, dataStorage.BUY_ORDER_BOOK_CACHE);
				}
				
				if(null != priceString) {
					orderPojo.setPrice(new BigDecimal(priceString));
				}
			}
			
			if(null != orderPojo.getPriceKey()) {
				TreeSet<OrderPojo> orderQueue = null;
				if(OrderType.BUY.equals(orderPojo.getOrderType())) {
					orderQueue = dataStorage.BUY_ORDER_BOOK_CACHE.get(orderPojo.getPriceKey());
				}else {
					orderQueue = dataStorage.SELL_ORDER_BOOK_CACHE.get(orderPojo.getPriceKey());
				}
				
				if(null == orderQueue) {
					orderQueue = new TreeSet<OrderPojo>();
				}
				orderQueue.add(orderPojo);
				
				if(OrderType.BUY.equals(orderPojo.getOrderType())) {
					dataStorage.BUY_ORDER_BOOK_CACHE.put(orderPojo.getPriceKey(), orderQueue);
				}else {
					dataStorage.SELL_ORDER_BOOK_CACHE.put(orderPojo.getPriceKey(), orderQueue);
				}
				
			}
			
			orderBookLock.unlock();
			dataStorage.PROCESSING_TASK_QUEUE.add(orderPojo);
		}
		
	}

	@Override
	public String getSortPrice(boolean isMax, HashMap<String, TreeSet<OrderPojo>> targetQueue) {
		List<String> priceKeys = targetQueue.keySet().stream().sorted((key1, key2) -> { 
			if(isMax) {
				return new BigDecimal(key2).compareTo(new BigDecimal(key1));
			}else {
				return new BigDecimal(key1).compareTo(new BigDecimal(key2));
			} 
		}).collect(Collectors.toList());
		
		for(String priceKey : priceKeys) {
			if (targetQueue.get(priceKey).stream().filter(order -> PriceType.LIMIT.equals(order.getPriceType()))
					.findAny().isPresent()) {
				return priceKey;
			}
		}
		return null;
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
