package com.example.tradeEngine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.example.tradeEngine.common.DataStorage;
import com.example.tradeEngine.common.OrderStatus;
import com.example.tradeEngine.pojo.OrderPojo;

public class TrandEngineService implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(TrandEngineService.class);
	
	private OrderService orderService;
	private DataStorage dataStoage;
	
	public TrandEngineService(ApplicationContext ctx) {
		this.orderService = ctx.getBean(OrderService.class);
		this.dataStoage = ctx.getBean(DataStorage.class);
	}
	
	@Override
	public void run() {
		OrderPojo filledOrder = dataStoage.FILLED_TASK_QUEUE.poll();
		if(null != filledOrder) {
			orderService.update(filledOrder);
		}
		OrderPojo orderPojo = dataStoage.PROCESSING_TASK_QUEUE.poll();
		if(null != orderPojo) {
			try {
				OrderPojo resultOrderPojo = orderService.processMatchEngine(orderPojo);
				if(OrderStatus.PROCESSING.equals(resultOrderPojo.getOrderStatus()) && resultOrderPojo.getQuantity() > 0L) {
					dataStoage.PROCESSING_TASK_QUEUE.offer(resultOrderPojo);
				}
			} catch (Exception e) {
				LOGGER.error(e.getMessage(),e);
				//Return order to queue.
				dataStoage.PROCESSING_TASK_QUEUE.offer(orderPojo);
			}

		}
	}

}
