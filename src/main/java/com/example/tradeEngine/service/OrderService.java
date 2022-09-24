package com.example.tradeEngine.service;

import java.util.HashMap;
import java.util.TreeSet;

import com.example.tradeEngine.pojo.OrderPojo;

public interface OrderService {
	public OrderPojo processMatchEngine(OrderPojo orderPojo) throws Exception;
	public void insert(OrderPojo orderPojo);
	public String getSortPrice(boolean isMax, HashMap<String, TreeSet<OrderPojo>> targetQueue);
	public void update(OrderPojo orderPojo);
}
