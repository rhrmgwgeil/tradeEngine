package com.example.tradeEngine.common;

import java.time.LocalDateTime;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Component;

import com.example.tradeEngine.pojo.OrderPojo;

@Component
public class DataStorage {
	// Cache service
	public ConcurrentHashMap<String, LocalDateTime> DISTRIBUTED_LOCK = new ConcurrentHashMap<String, LocalDateTime>();
	public ConcurrentHashMap<String, TreeSet<OrderPojo>> SELL_ORDER_BOOK_CACHE = new ConcurrentHashMap<String, TreeSet<OrderPojo>>();
	public ConcurrentHashMap<String, TreeSet<OrderPojo>> BUY_ORDER_BOOK_CACHE = new ConcurrentHashMap<String, TreeSet<OrderPojo>>();
	
	// Message queue
	public ConcurrentLinkedQueue<OrderPojo> PROCESSING_TASK_QUEUE = new ConcurrentLinkedQueue<OrderPojo>();
	public ConcurrentLinkedQueue<OrderPojo> FILLED_TASK_QUEUE = new ConcurrentLinkedQueue<OrderPojo>();
	
	// Database
	public ConcurrentHashMap<UUID, OrderPojo> ORDER_BOOK_STORAGE = new ConcurrentHashMap<UUID, OrderPojo>();
}
