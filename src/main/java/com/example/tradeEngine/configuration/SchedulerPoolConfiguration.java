package com.example.tradeEngine.configuration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import com.example.tradeEngine.service.TrandEngineService;

@Component
public class SchedulerPoolConfiguration {
	
	@Autowired
	private ThreadPoolTaskScheduler threadPoolTaskScheduler;
	
	@Autowired
	private ApplicationContext appContext;
	private Integer maxPoolSize = 4;
	
	@Bean("threadPoolTaskScheduler")
	public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
		threadPoolTaskScheduler.setPoolSize(maxPoolSize);
		threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
		return threadPoolTaskScheduler;
	}
	
	@PostConstruct
	public void init() {
		for(int i=1 ; i <= maxPoolSize ; i++) {
			this.threadPoolTaskScheduler.scheduleWithFixedDelay(new TrandEngineService(appContext), 1000 * i);
		}
	}
}
