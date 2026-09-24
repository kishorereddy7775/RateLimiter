package com.service;

import com.entities.Request;

public interface RateLimiter {
	
	public boolean isRequestAllowed(Request request);

}
