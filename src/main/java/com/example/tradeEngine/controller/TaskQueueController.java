package com.example.tradeEngine.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.tradeEngine.common.DataStorage;
import com.example.tradeEngine.pojo.OrderPojo;

@Controller
@RequestMapping("/queues")
public class TaskQueueController {

	@Autowired
	private DataStorage datatDataStorage;

	@GetMapping(value = "sells")
	public ResponseEntity<ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<OrderPojo>>> getSellOrders() {
		ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<OrderPojo>> resultMap = datatDataStorage.SELL_ORDER_BOOK_CACHE;
		return ResponseEntity.ok(resultMap);
	}

	@GetMapping(value = "buys")
	public ResponseEntity<ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<OrderPojo>>> getBuyOrders() {
		ConcurrentSkipListMap<BigDecimal, ConcurrentSkipListSet<OrderPojo>> resultMap = datatDataStorage.BUY_ORDER_BOOK_CACHE;
		return ResponseEntity.ok(resultMap);
	}

	@GetMapping(value = "Tasks")
	public ResponseEntity<List<OrderPojo>> getAllOrders() {
		List<OrderPojo> resultList = new ArrayList<OrderPojo>(datatDataStorage.PROCESSING_TASK_QUEUE);
		return ResponseEntity.ok(resultList);
	}
}
