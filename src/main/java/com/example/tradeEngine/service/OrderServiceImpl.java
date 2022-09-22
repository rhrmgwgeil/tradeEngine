package com.example.tradeEngine.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Optional;
import java.util.TreeSet;

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
import com.example.tradeEngine.pojo.OrderPojo;

@Service
public class OrderServiceImpl implements OrderService {
	private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);
	
	@Autowired
	private OrderDao orderDao;
	@Autowired
	private DataStorage dataStorage;

	@Override
	public OrderPojo processMatchEngine(OrderPojo orderPojo) {
		OrderPojo currentOrderEntity = orderDao.findById(orderPojo.getId());
		if(OrderStatus.PROCESSING.equals(currentOrderEntity.getOrderStatus())) {
			String targetPrice = orderPojo.getPriceKey();
			
			Boolean isBuyingOrder = OrderType.BUY.equals(orderPojo.getOrderType());
			Boolean isLimitOrder = PriceType.LIMIT.equals(orderPojo.getPriceType());
			
			if(!isLimitOrder) {
				if(isBuyingOrder) {
					Optional<String> minSellPrice = dataStorage.SELL_ORDER_BOOK_CACHE.keySet().stream().sorted((key1, key2) -> new BigDecimal(key1).compareTo(new BigDecimal(key2))).findFirst();
					targetPrice = minSellPrice.isPresent() ? minSellPrice.get() : null;
				}else {
					Optional<String> maxBuyPrice = dataStorage.BUY_ORDER_BOOK_CACHE.keySet().stream().sorted((key1, key2) -> new BigDecimal(key2).compareTo(new BigDecimal(key1))).findFirst();
					targetPrice = maxBuyPrice.isPresent() ? maxBuyPrice.get() : null;
				}
			}
			
			String buyLockKey = DistributedLockKey.BUY_ORDER_CACHE.genereateLockKey(targetPrice);
			String sellLockKey = DistributedLockKey.SELL_ORDER_CACHE.genereateLockKey(targetPrice);
			
			CacheDistributeLock buyLock = new CacheDistributeLock(dataStorage.DISTRIBUTED_LOCK, buyLockKey, null);
			CacheDistributeLock sellLock = new CacheDistributeLock(dataStorage.DISTRIBUTED_LOCK, sellLockKey, null);
	
			if(null != buyLock.lock() && null != sellLock.lock()) {
				LOGGER.info(String.format("Start match, Price: %s, Quantity: %d, Price Type: %s, Target type: %s", targetPrice, orderPojo.getQuantity(), orderPojo.getPriceType(), orderPojo.getOrderType()));
				
				OrderPojo currentCacheOrder = null;
				TreeSet<OrderPojo> targetQueue = null;
				TreeSet<OrderPojo> currentQueue = null;
				
				if(isBuyingOrder) {
					currentQueue = dataStorage.BUY_ORDER_BOOK_CACHE.get(targetPrice);
					targetQueue = dataStorage.SELL_ORDER_BOOK_CACHE.get(targetPrice);
					if(currentQueue != null) {
						currentCacheOrder = currentQueue.pollFirst();
					}
				}else {
					currentQueue = dataStorage.SELL_ORDER_BOOK_CACHE.get(targetPrice);
					targetQueue = dataStorage.BUY_ORDER_BOOK_CACHE.get(targetPrice);
					if(currentQueue != null) {
						currentCacheOrder = currentQueue.pollFirst();
					}
				}
				
				if(!isLimitOrder) {
					OrderPojo limitOrderPojo = currentCacheOrder;
					currentCacheOrder = null;
					if(null != limitOrderPojo) {
						currentQueue.add(limitOrderPojo);
						if(limitOrderPojo.getCreateTime().isAfter(orderPojo.getCreateTime())) {
							currentCacheOrder = orderPojo;
						}
					}else {
						currentCacheOrder = orderPojo;
					}
				}
			
				Long currentQuantity = orderPojo.getQuantity();
				if(null != currentCacheOrder && currentCacheOrder.getId().equals(orderPojo.getId())) {
					while (currentQuantity > 0L) {
						if(null != targetQueue) {
							OrderPojo targetOrderPojo = targetQueue.pollFirst();
							if(null != targetOrderPojo) {
								OrderPojo targetOrderEntity = orderDao.findById(targetOrderPojo.getId());
								
								if((currentQuantity - targetOrderPojo.getQuantity()) >= 0) {
									// The target order can be filled. Remove order from queue.
									currentQuantity = currentQuantity - targetOrderPojo.getQuantity();
									
									targetOrderEntity.getMatchOrderId().add(orderPojo.getId());
									targetOrderEntity.setOrderStatus(OrderStatus.FILLED);
									orderDao.update(targetOrderEntity);
								}else {
									// The target order can be partially filled.
									currentQuantity = 0L;
									targetOrderPojo.setQuantity(targetOrderPojo.getQuantity() - currentQuantity);
									targetQueue.add(targetOrderPojo);
									
									targetOrderEntity.getMatchOrderId().add(orderPojo.getId());
									orderDao.update(targetOrderEntity);
								}
								// Update entity related ID
								currentOrderEntity.getMatchOrderId().add(targetOrderEntity.getId());
								continue;
							}
						}
						break;
					}
					
					if(currentQuantity == 0) {
						// Remove current order from queue when it filled.
						orderPojo = null;
					}else {
						// Update current order quantity and put in queue.
						orderPojo.setQuantity(currentQuantity);
					}
					
					currentOrderEntity.setOrderStatus(currentQuantity > 0L ? OrderStatus.PROCESSING : OrderStatus.FILLED);
					orderDao.update(currentOrderEntity);
				}
				
				if(null != currentCacheOrder && isLimitOrder && currentQuantity > 0L) {
					currentQueue.add(currentCacheOrder);
				}
				
				updateQueue(isBuyingOrder, targetPrice, currentQueue);
				updateQueue(!isBuyingOrder, targetPrice, targetQueue);
			}
			
			if(null != buyLock.lock()) {
				buyLock.unlock();
			}
			if(null != sellLock.lock()) {
				sellLock.unlock();
			}
		}else {
			// Remove task for queue when the order status isn't in processing.
			orderPojo = null;
		}
		
		return orderPojo;
	}

	@Override
	public void insert(OrderPojo orderPojo) {
		orderPojo = orderDao.insert(orderPojo);
		
		if(PriceType.LIMIT.equals(orderPojo.getPriceType())) {
			HashMap<String, TreeSet<OrderPojo>> targetMap = OrderType.BUY.equals(orderPojo.getOrderType()) ? dataStorage.BUY_ORDER_BOOK_CACHE : dataStorage.SELL_ORDER_BOOK_CACHE;
			String lockKey = OrderType.BUY.equals(orderPojo.getOrderType()) ? 
					DistributedLockKey.BUY_ORDER_CACHE.genereateLockKey(orderPojo.getPrice()) :  DistributedLockKey.SELL_ORDER_CACHE.genereateLockKey(orderPojo.getPrice());
			
			CacheDistributeLock cacheLock = new CacheDistributeLock(dataStorage.DISTRIBUTED_LOCK, lockKey , null).lock();
			if(null != cacheLock) {
				TreeSet<OrderPojo> targetQueue = targetMap.get(orderPojo.getPriceKey());
				if(null == targetQueue) {
					targetQueue = new TreeSet<OrderPojo>();
				}
				
				targetQueue.add((OrderPojo)orderPojo.clone());
				targetMap.put(orderPojo.getPriceKey(), targetQueue);
				
				cacheLock.unlock();
			}else {
				LOGGER.error("Can't insert order into queue.");
			}
		}
		dataStorage.MATCH_TASK_QUEUE.add(orderPojo);
	}
	
	protected void updateQueue (Boolean isBuyingOrder, String targetPrice, TreeSet<OrderPojo> targetQueue) {
		if(null != targetQueue && targetQueue.size() > 0) {
			if(isBuyingOrder) {
				dataStorage.BUY_ORDER_BOOK_CACHE.put(targetPrice, targetQueue);
			}else {
				dataStorage.SELL_ORDER_BOOK_CACHE.put(targetPrice, targetQueue);
			}
		}else {
			if(isBuyingOrder) {
				dataStorage.BUY_ORDER_BOOK_CACHE.remove(targetPrice);
			}else {
				dataStorage.SELL_ORDER_BOOK_CACHE.remove(targetPrice);
			}
		}
	}
}
