package com.example.tradeEngine.service;

import java.util.UUID;

import com.example.tradeEngine.pojo.OrderPojo;

public interface OrderDao {
	public OrderPojo insert(OrderPojo orderPojo);
	public void update(OrderPojo orderPOjo);
	public OrderPojo findById(UUID id);
}
