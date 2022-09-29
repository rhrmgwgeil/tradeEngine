package com.example.tradeEngine.common;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheDistributeLock {
	private final static Logger LOGGER = LoggerFactory.getLogger(CacheDistributeLock.class);
	private final static Integer DEFAULT_RETRY_TIMES = 3;
	private final static Integer DEFAULT_RETRY_SECOND = 2000;

	private ConcurrentHashMap<String, LocalDateTime> distributedLock;
	private String key;
	private Long timeoutSecond;
	
	public CacheDistributeLock(ConcurrentHashMap<String, LocalDateTime> distributedLock, String key) {
		this.key = key;
		this.distributedLock = distributedLock;
	}
	
	public void unlock() {
		this.distributedLock.remove(this.key);
	}
	
	public CacheDistributeLock lock() {
		Integer reTryTimes = DEFAULT_RETRY_TIMES;
		do {
			if(!this.distributedLock.containsKey(this.key)) {
				this.distributedLock.put(this.key, LocalDateTime.now().plusSeconds(this.timeoutSecond));
				return this;
			}

			if (reTryTimes > 0) {
				try {
					TimeUnit.MILLISECONDS.sleep(DEFAULT_RETRY_SECOND);
				} catch (InterruptedException e) {
					LOGGER.error(e.getMessage(), e);
				}
			}
			if (Thread.currentThread().isInterrupted()) {
				break;
			}
		} while (reTryTimes-- > 0);
		LOGGER.info("Can't lock KEY : " + key + " in Cache. ");
		return null;
	}
}
