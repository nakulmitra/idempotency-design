package com.devportal.paymentsystem.service;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisLockService {

	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	public boolean acquireLock(String lockKey, String requestId, long timeoutSeconds) {
		Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, requestId, timeoutSeconds, TimeUnit.SECONDS);
		return Boolean.TRUE.equals(success);
	}

	public void releaseLock(String lockKey, String requestId) {
		String value = redisTemplate.opsForValue().get(lockKey);
		
		if(requestId.equals(value)) {
			redisTemplate.delete(lockKey);
		}
	}
}
