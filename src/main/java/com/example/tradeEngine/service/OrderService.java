package com.example.tradeEngine.service;

import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.example.tradeEngine.pojo.OrderPojo;

public interface OrderService {
	public OrderPojo processMatchEngine(OrderPojo orderPojo) throws Exception;
	public void insert(OrderPojo orderPojo);
	public String getSortPrice(boolean isMax, ConcurrentHashMap<String, TreeSet<OrderPojo>> targetQueue);
	public void update(OrderPojo orderPojo);
}
