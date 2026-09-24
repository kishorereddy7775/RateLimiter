package com.entities;

import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SlidingWindowRateLimitingState {

	private Queue<LocalDateTime> q;
	
	public SlidingWindowRateLimitingState() {
		q=new ConcurrentLinkedQueue<LocalDateTime>();
	}
	
	public void addRequest(LocalDateTime current) {
		q.add(current);
	}
	public void evictOldRequests(int timeFrame) {
		LocalDateTime current=LocalDateTime.now();
		while(!q.isEmpty() && q.peek().isBefore(current.minusSeconds(timeFrame))) {
			q.poll();
		}
	}
	public boolean isSpaceAvailble(int size) {
		return q.size()<size;
	}
}
