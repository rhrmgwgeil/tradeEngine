package com.example.tradeEngine.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.tradeEngine.common.DataStorage;
import com.example.tradeEngine.common.OrderStatus;
import com.example.tradeEngine.pojo.OrderPojo;

@Service
public class OrderDaoImpl implements OrderDao {
	private static final Logger LOGGER = LoggerFactory.getLogger(OrderDaoImpl.class);
	
	@Autowired
	private DataStorage dataStorage;
	
	@Override
	public OrderPojo insert(OrderPojo orderPojo) {	
		orderPojo.setId(UUID.randomUUID());
		orderPojo.setOrderStatus(OrderStatus.PROCESSING);
		orderPojo.setCreateTime(LocalDateTime.now());
		orderPojo.setUpdateTime(LocalDateTime.now());
		dataStorage.ORDER_BOOK_STORAGE.put(orderPojo.getId(),(OrderPojo)orderPojo.clone());
		return orderPojo;
	}

	@Override
	public void update(OrderPojo orderPojo) {
		OrderPojo preOrderPojo = dataStorage.ORDER_BOOK_STORAGE.put(orderPojo.getId(), orderPojo);
		if(null == preOrderPojo){
			LOGGER.error("Update failed, Can't find order by ID "+ orderPojo.getId());
		}
	}

	@Override
	public OrderPojo findById(UUID id) {
		return dataStorage.ORDER_BOOK_STORAGE.get(id);
	}

}
