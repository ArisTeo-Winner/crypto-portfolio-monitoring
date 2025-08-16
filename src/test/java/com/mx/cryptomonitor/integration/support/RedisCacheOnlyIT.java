package com.mx.cryptomonitor.integration.support;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class RedisCacheOnlyIT {
	
	  @Bean 
	  TestProbeService testProbeService() { 
		  return new TestProbeService(); 
		  }

	  public static class TestProbeService {
		  
	    private final AtomicInteger counter = new AtomicInteger(0);
	    
	    @Cacheable(value = "testCache", key = "#k")
	    public String expensive(String k) { return "v" + counter.incrementAndGet(); 
	    }
	    
	    public int calls() { 
	    	return counter.get(); 
	    	}
	  }
}
