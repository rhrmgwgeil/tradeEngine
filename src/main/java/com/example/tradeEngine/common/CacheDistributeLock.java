package com.example.tradeEngine.common;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class CacheDistributeLock {
	private final static Logger LOGGER = LoggerFactory.getLogger(CacheDistributeLock.class);
	private final static Integer DEFAULT_RETRY_TIMES = 3;
	private final static Integer DEFAULT_RETRY_MILLIS = 500;

	private final ConcurrentHashMap<String, LocalDateTime> distributedLock = new ConcurrentHashMap<>();

	/**
	 * Try to get a lock by key with timeout second
	 * 
	 * @param key
	 * @param timeoutSeconds
	 * @return true or false
	 */
	public boolean lock(String key, long timeoutSeconds) {
		int reTryTimes = DEFAULT_RETRY_TIMES;

		do {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime expireTime = now.plusSeconds(timeoutSeconds);

			if (distributedLock.putIfAbsent(key, expireTime) == null) {
				return true;
			}

			LocalDateTime currentLockExpireTime = distributedLock.get(key);

			if (currentLockExpireTime != null && currentLockExpireTime.isBefore(now)) {
				if (distributedLock.replace(key, currentLockExpireTime, expireTime)) {
					LOGGER.info("Replace key successfuly.. KEY : {}", key);
					return true;
				}
			}

			if (reTryTimes > 0) {
				try {
					TimeUnit.MILLISECONDS.sleep(DEFAULT_RETRY_MILLIS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		} while (reTryTimes-- > 0);

		LOGGER.warn("Faild to get a lock, KEY : {}", key);
		return false;
	}

	public void unlock(String key) {
		distributedLock.remove(key);
	}
}
