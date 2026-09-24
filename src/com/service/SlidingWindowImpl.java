package com.service;

import java.util.ArrayDeque;
import java.util.Queue;

import com.entities.Request;

public class SlidingWindowImpl implements RateLimiter{
	
	//Assuming we are implementing this rate limiter for one type of request for now
	private Queue<Request> requestQueue;
	private int allowedLimit;
	private int timeFrameinSeconds;
	
	public SlidingWindowImpl(int limit, int timeFrame) {
		requestQueue = new ArrayDeque<>();
		allowedLimit=limit;
		timeFrameinSeconds=timeFrame;
		
	}
	
	public boolean isRequestAllowed(Request request) {
		evictOldRequests(request);
		if(requestQueue.size()<allowedLimit) {
			requestQueue.add(request);
			return true;
		}
		return false;
	}
	
	private void evictOldRequests(Request request) {
		while(!requestQueue.isEmpty() && requestQueue.peek().timeStamp().isBefore(request.timeStamp().minusSeconds(timeFrameinSeconds))) {
			requestQueue.poll();
		}
	}
}
