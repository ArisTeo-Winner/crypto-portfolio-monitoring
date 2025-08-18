package com.mx.cryptomonitor.integration.support;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
//@Profile("test")
public class TestProbeService {

	  private final AtomicInteger counter = new AtomicInteger(0);

	  @Cacheable(value = "testCache", key = "#k")
	  public String expensive(String k) {
	    // Simula trabajo costoso; solo debería ejecutarse en el miss
	    return "v" + counter.incrementAndGet();
	  }

	  public int calls() { 
		  return counter.get(); 
		  }
	}

