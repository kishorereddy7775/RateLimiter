package com.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.entities.SlidingWindowRateLimitingState;
import com.entities.Request;

public class SlidingWindowImpl implements RateLimiter{
	
	Map<Integer, SlidingWindowRateLimitingState> requestStates;
	private int allowedLimit;
	private int timeFrameinSeconds;
	
	public SlidingWindowImpl(int limit, int timeFrame) {
		requestStates = new ConcurrentHashMap<>();
		allowedLimit=limit;
		timeFrameinSeconds=timeFrame;
	}
	
	public boolean isRequestAllowed(Request request) {
		requestStates.putIfAbsent(request.client(), new SlidingWindowRateLimitingState());
		SlidingWindowRateLimitingState state=requestStates.get(request.client());
		state.evictOldRequests(timeFrameinSeconds);
		synchronized (state) {
			if(state.isSpaceAvailble(allowedLimit)) {
				state.addRequest(LocalDateTime.now());
				return true;
			}
		}
		return false;
	}
	
}
