package com.test;

import java.time.LocalDateTime;

import com.entities.Request;
import com.service.RateLimiter;
import com.service.SlidingWindowImpl;
import com.service.TokenBucketImpl;

public class Test {
	
	public static void main(String[] args) {
		
		RateLimiter rateLimiter=new SlidingWindowImpl(5, 30);
		
		System.out.println(rateLimiter.isRequestAllowed(new Request(1,"MyClient","####",LocalDateTime.now())));
		System.out.println(rateLimiter.isRequestAllowed(new Request(2,"MyClient","####",LocalDateTime.now().plusSeconds(3))));
		System.out.println(rateLimiter.isRequestAllowed(new Request(3,"MyClient","####",LocalDateTime.now().plusSeconds(3))));
		System.out.println(rateLimiter.isRequestAllowed(new Request(4,"MyClient","####",LocalDateTime.now().plusSeconds(3))));
		System.out.println(rateLimiter.isRequestAllowed(new Request(5,"MyClient","####",LocalDateTime.now().plusSeconds(20))));
		System.out.println(rateLimiter.isRequestAllowed(new Request(6,"MyClient","####",LocalDateTime.now().plusSeconds(29))));
		
		System.out.println("Token Bucket--------");
		
		TokenBucketImpl tokenBucketImpl = new TokenBucketImpl(5, 30);
		Thread myThread=new Thread(tokenBucketImpl);
		myThread.start();
		System.out.println(tokenBucketImpl.isRequestAllowed(new Request(1,"MyClient","####",LocalDateTime.now())));
		System.out.println(tokenBucketImpl.isRequestAllowed(new Request(2,"MyClient","####",LocalDateTime.now().plusSeconds(3))));
		System.out.println(tokenBucketImpl.isRequestAllowed(new Request(3,"MyClient","####",LocalDateTime.now().plusSeconds(3))));
		System.out.println(tokenBucketImpl.isRequestAllowed(new Request(4,"MyClient","####",LocalDateTime.now().plusSeconds(3))));
		System.out.println(tokenBucketImpl.isRequestAllowed(new Request(5,"MyClient","####",LocalDateTime.now().plusSeconds(20))));
		System.out.println(tokenBucketImpl.isRequestAllowed(new Request(6,"MyClient","####",LocalDateTime.now().plusSeconds(29))));
		try {
			Thread.sleep(15*1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(tokenBucketImpl.isRequestAllowed(new Request(1,"MyClient","####",LocalDateTime.now())));
		try {
			Thread.sleep(15*1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(tokenBucketImpl.isRequestAllowed(new Request(1,"MyClient","####",LocalDateTime.now())));
	}

}
