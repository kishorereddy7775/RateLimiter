package com.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.entities.Request;
import com.entities.TokenBucketRateLimitingState;

public class TokenBucketImpl implements RateLimiter {
	
	private Map<Integer, TokenBucketRateLimitingState> requestStates;
	private int capacity;
	private int refillRate; //per 1 min
	public TokenBucketImpl(int capacity, int refillRate) {
		requestStates=new ConcurrentHashMap<>();
		this.capacity=capacity;
		this.refillRate=refillRate;
	}
	
	public boolean isRequestAllowed(Request request) {
		requestStates.putIfAbsent(request.client(), new TokenBucketRateLimitingState());
		TokenBucketRateLimitingState currentState=requestStates.get(request.client());
		currentState.refillToken(refillRate, capacity);
		if(currentState.isTokenAvailable()) {
			currentState.decrementToken();
			return true;
		}
		return false;
	}
}
