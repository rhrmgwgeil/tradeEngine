package com.example.tradeEngine.controller;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.tradeEngine.common.DataStorage;
import com.example.tradeEngine.common.PriceType;
import com.example.tradeEngine.pojo.OrderPojo;
import com.example.tradeEngine.service.OrderService;

@Controller
@RequestMapping("/orders")
public class OrderController {
	private static Logger LOGGER = LoggerFactory.getLogger(OrderController.class);
	
	@Autowired
	private OrderService orderService;
	
	@Autowired
	private DataStorage datatDataStorage;
	
	@PostMapping
	public ResponseEntity<OrderPojo> createOrder(@RequestBody OrderPojo orderPojo){
		if(PriceType.MARKET.equals(orderPojo.getPriceType())) {
			orderPojo.setPrice(null);
		}
		orderService.insert(orderPojo);
		return ResponseEntity.ok(orderPojo);
	}
	
	@GetMapping
	public ResponseEntity<List<OrderPojo>> getOrders(){
		List<OrderPojo> resultList = new ArrayList<OrderPojo>(datatDataStorage.ORDER_BOOK_STORAGE.values());
		return ResponseEntity.ok(resultList);
	}
}
