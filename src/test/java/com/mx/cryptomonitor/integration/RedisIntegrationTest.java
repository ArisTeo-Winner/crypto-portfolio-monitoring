/**
 * 
 */
package com.mx.cryptomonitor.integration;


import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.mx.cryptomonitor.infrastructure.api.MarketDataService;

/**
 * 
 */


class RedisIntegrationTest {
	
    private final Logger logger = LoggerFactory.getLogger(RedisIntegrationTest.class);
    
    @Autowired
    private MarketDataService marketDataService;
    
    private final String SYMBOL_ETH = "ETH";



    @Autowired
    void dumpCacheInfra(ApplicationContext ctx) {
      System.out.println("Interceptors: " +
        Arrays.toString(ctx.getBeanNamesForType(
          org.springframework.cache.interceptor.CacheInterceptor.class)));
      System.out.println("Advisors: " +
        Arrays.toString(ctx.getBeanNamesForType(
          org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor.class)));
    }


}
