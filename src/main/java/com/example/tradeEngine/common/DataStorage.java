package com.example.tradeEngine.common;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Component;

import com.example.tradeEngine.pojo.OrderPojo;

@Component
public class DataStorage {
	public HashMap<String, LocalDateTime> DISTRIBUTED_LOCK = new HashMap<String, LocalDateTime>();
	public HashMap<String, TreeSet<OrderPojo>> SELL_ORDER_BOOK_CACHE = new HashMap<String, TreeSet<OrderPojo>>();
	public HashMap<String, TreeSet<OrderPojo>> BUY_ORDER_BOOK_CACHE = new HashMap<String, TreeSet<OrderPojo>>();
	
	public ConcurrentHashMap<UUID, OrderPojo> ORDER_BOOK_STORAGE = new ConcurrentHashMap<UUID, OrderPojo>();
	public ConcurrentLinkedQueue<OrderPojo> MATCH_TASK_QUEUE = new ConcurrentLinkedQueue<OrderPojo>();
}
