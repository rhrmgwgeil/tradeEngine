package com.example.tradeEngine.common;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheDistributeLock {
	private final static Logger LOGGER = LoggerFactory.getLogger(CacheDistributeLock.class);
	private final static Long DEFAULT_TIMEOUT_SECOND = 900L;
	private final static Integer DEFAULT_RETRY_TIMES = 3;
	private final static Integer DEFAULT_RETRY_SECOND = 2000;
	
	private HashMap<String, LocalDateTime> distributedLock;
	private String key;
	private Long timeoutSecond;
	
	public CacheDistributeLock(HashMap<String, LocalDateTime> distributedLock, String key, Long timeoutSecond) {
		this.key = key;
		this.distributedLock = distributedLock;
		if(null != timeoutSecond) {
			this.timeoutSecond = timeoutSecond;
		}else {
			this.timeoutSecond = DEFAULT_TIMEOUT_SECOND;
		}
	}
	
	public void unlock() {
		this.distributedLock.remove(this.key);
	}
	
	public CacheDistributeLock lock() {
		Integer reTryTimes = DEFAULT_RETRY_TIMES;
		do {
			if(!this.distributedLock.containsKey(this.key) || LocalDateTime.now().isAfter(this.distributedLock.get(this.key))) {
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
