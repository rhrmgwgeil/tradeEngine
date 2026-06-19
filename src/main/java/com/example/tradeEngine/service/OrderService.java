package com.example.tradeEngine.service;

import com.example.tradeEngine.pojo.OrderPojo;

public interface OrderService {
	public OrderPojo processMatchEngine(OrderPojo orderPojo) throws Exception;

	public void insert(OrderPojo orderPojo);

	public void update(OrderPojo orderPojo);
}
