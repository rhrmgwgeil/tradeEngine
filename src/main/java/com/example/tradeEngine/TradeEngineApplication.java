package com.example.tradeEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "com.example" })
public class TradeEngineApplication {
	private static final Logger LOGGER = LoggerFactory.getLogger(TradeEngineApplication.class);
	
	public static void main(String[] args) {
		SpringApplication.run(TradeEngineApplication.class, args);
		LOGGER.info("Service Start.");
	}

}
