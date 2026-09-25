package com.entities;

import java.io.SyncFailedException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TokenBucketRateLimitingState {

	private AtomicInteger tokens;
	private LocalDateTime lastRefillTime;
	
	public TokenBucketRateLimitingState() {
		tokens =new AtomicInteger(0);
		lastRefillTime = null;
	}
	
	
	public void refillToken(int refillRate, int capacity) {
		if(tokens.get()==capacity)
			return;
		if(lastRefillTime==null) {
			updateToken(capacity);
			return;
		}
		long diff=ChronoUnit.MINUTES.between(lastRefillTime,LocalDateTime.now());
		int val=Math.min(tokens.get()+((int)diff*refillRate), capacity);
		if(val>0)
			updateToken(val);
	}
	
	private synchronized void updateToken(int val) {
		tokens.set(val);
		lastRefillTime=LocalDateTime.now();
	}
	
	public boolean isTokenAvailable() {
		return tokens.get()>0;
	}
	public void decrementToken() {
		tokens.decrementAndGet();
	}
}
