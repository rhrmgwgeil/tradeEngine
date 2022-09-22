package com.example.tradeEngine.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

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
	public ResponseEntity<HashMap<String, TreeSet<OrderPojo>>> getSellOrders(){
		HashMap<String, TreeSet<OrderPojo>> resultMap = datatDataStorage.SELL_ORDER_BOOK_CACHE;
		return ResponseEntity.ok(resultMap);
	}
	
	@GetMapping(value = "buys")
	public ResponseEntity<HashMap<String, TreeSet<OrderPojo>>> getBuyOrders(){
		HashMap<String, TreeSet<OrderPojo>> resultMap = datatDataStorage.BUY_ORDER_BOOK_CACHE;
		return ResponseEntity.ok(resultMap);
	}
	
	@GetMapping(value = "Tasks")
	public ResponseEntity<List<OrderPojo>> getAllOrders(){
		List<OrderPojo> resultList = new ArrayList<OrderPojo>(datatDataStorage.MATCH_TASK_QUEUE);
		return ResponseEntity.ok(resultList);
	}
}
