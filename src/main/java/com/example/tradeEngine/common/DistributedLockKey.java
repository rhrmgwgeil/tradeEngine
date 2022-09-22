package com.example.tradeEngine.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public enum DistributedLockKey {
	BUY_ORDER_CACHE, 
	SELL_ORDER_CACHE,
	ORDER_BOOK_STORAGE;
	
	public String genereateLockKey(Object key) {
		String resultKey = String.format("%s", this.name());
		if(null != key) {
			String subKey = "";
			if(key instanceof UUID){
				UUID uuidKey = (UUID)key;
				subKey = uuidKey.toString();
			}else if(key instanceof BigDecimal) {
				BigDecimal bigDecimalKey = (BigDecimal)key;
				subKey = bigDecimalKey.setScale(2, RoundingMode.HALF_UP).toString();
			}else {
				subKey = key.toString();
			}
			resultKey = String.format("%s-%s", resultKey, subKey);
		}
		return resultKey;
	}
}
