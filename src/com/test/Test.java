package com.test;

import java.time.LocalDateTime;

import com.entities.Request;
import com.service.RateLimiter;
import com.service.SlidingWindowImpl;
import com.service.TokenBucketImpl;

public class Test {
	
	public static void main(String[] args) throws InterruptedException {
		
		RateLimiter rateLimiter=new SlidingWindowImpl(2, 10);
		
		System.out.println(rateLimiter.isRequestAllowed(new Request(1,1)));
		System.out.println(rateLimiter.isRequestAllowed(new Request(2,2)));
		System.out.println(rateLimiter.isRequestAllowed(new Request(3,1)));
		Thread.sleep(5*1000);
		System.out.println(rateLimiter.isRequestAllowed(new Request(4,1)));
		Thread.sleep(5*1000);
		System.out.println(rateLimiter.isRequestAllowed(new Request(5,1)));
		
		System.out.println("---------------Token Bucket----------------");
		
		RateLimiter rateLimiter2 = new TokenBucketImpl(2, 2);
		System.out.println(rateLimiter2.isRequestAllowed(new Request(1,1)));
		System.out.println(rateLimiter2.isRequestAllowed(new Request(2,1)));
		System.out.println(rateLimiter2.isRequestAllowed(new Request(3,1)));
		
		
	}

}
