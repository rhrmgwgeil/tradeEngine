package com.example.tradeEngine.service;

import org.springframework.context.ApplicationContext;

import com.example.tradeEngine.common.DataStorage;
import com.example.tradeEngine.pojo.OrderPojo;

public class TrandEngineService implements Runnable {
	
	private OrderService orderService;
	private DataStorage dataStoage;
	
	public TrandEngineService(ApplicationContext ctx) {
		this.orderService = ctx.getBean(OrderService.class);
		this.dataStoage = ctx.getBean(DataStorage.class);
	}
	
	@Override
	public void run() {
		OrderPojo orderPojo = dataStoage.MATCH_TASK_QUEUE.poll();
		if(orderPojo != null) {
			OrderPojo resultOrderPojo = orderService.processMatchEngine(orderPojo);
			if(null != resultOrderPojo) {
				dataStoage.MATCH_TASK_QUEUE.offer(resultOrderPojo);
			}
		}
	}

}
