package com.example.tradeEngine.common;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.springframework.stereotype.Component;

import com.example.tradeEngine.pojo.OrderPojo;

@Component
public class DataStorage {

	// Sort by price order by Ascending
	public ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<OrderPojo>> SELL_ORDER_BOOK_CACHE = new ConcurrentSkipListMap<>();
	// Sort by price order by Descending
	public ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<OrderPojo>> BUY_ORDER_BOOK_CACHE = new ConcurrentSkipListMap<>(
			Comparator.reverseOrder());

	// FIFO Message queue
	public ConcurrentLinkedQueue<OrderPojo> PROCESSING_TASK_QUEUE = new ConcurrentLinkedQueue<OrderPojo>();
	// FIFO Message queue
	public ConcurrentLinkedQueue<OrderPojo> FILLED_TASK_QUEUE = new ConcurrentLinkedQueue<OrderPojo>();

	// Storage for all orders
	public ConcurrentHashMap<UUID, OrderPojo> ORDER_BOOK_STORAGE = new ConcurrentHashMap<UUID, OrderPojo>();
}
