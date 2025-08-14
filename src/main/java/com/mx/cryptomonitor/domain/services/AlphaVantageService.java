package com.mx.cryptomonitor.domain.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.mx.cryptomonitor.domain.ports.MarketDataProvider;
import com.mx.cryptomonitor.infrastructure.api.client.AlphaVantageAdapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlphaVantageService {

	private final MarketDataProvider marketDataProvider;  
	
    @Cacheable(value = "latestPrice", key = "#symbol")
    public BigDecimal getLatestStockPrice(String symbol) {
    	    	    
        return marketDataProvider.getLatest(symbol)
        		.orElseThrow(() -> new NoSuchElementException("No price found for symbol " + symbol));
    }
}
