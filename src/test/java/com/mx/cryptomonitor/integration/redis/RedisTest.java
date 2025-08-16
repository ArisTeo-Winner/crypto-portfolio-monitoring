package com.mx.cryptomonitor.integration.redis;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class RedisTest {	    

	@BeforeEach
	void setUp() throws Exception {
		
		
		Optional<BigDecimal> getCache=getStockPriceTest("12345");
		System.out.println("Datos para Cacheado en Redi: "+getCache);
	}		
	
	@Test
    @Cacheable(value = "stockPrices", key = "#symbol")
    public Optional<BigDecimal> getStockPriceTest(String symbol) {
        // Simula una operación costosa
    	 BigDecimal closePrice = new BigDecimal(symbol.toString());
        System.out.println("Obteniendo datos de la fuente para la clave: " + closePrice);
        return Optional.of(closePrice);
    }

}
