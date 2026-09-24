package com.service;

import com.entities.Request;

public class TokenBucketImpl implements RateLimiter, Runnable{
	
	private int allowedLimit;
	private int timeFrameInSeconds;
	private int BucketCounter=0;
	public TokenBucketImpl(int limit, int timeFrameInSeconds) {
		allowedLimit=limit;
		this.timeFrameInSeconds=timeFrameInSeconds;
	}

	public boolean isRequestAllowed(Request request) {
		if(BucketCounter>0) {
			BucketCounter--;
			return true;
		}
		return false;
	}

	@Override
	public void run() {
		while(true) {
			BucketCounter=allowedLimit;
			try {
				Thread.sleep(timeFrameInSeconds*1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
