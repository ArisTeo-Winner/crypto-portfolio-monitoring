package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mx.cryptomonitor.domain.ports.MarketDataProvider;
import com.mx.cryptomonitor.domain.services.AlphaVantageService;

@ExtendWith(MockitoExtension.class)
class AlphaVantageServiceTest {
	
	private static final Logger logger = LoggerFactory.getLogger(AlphaVantageService.class);

    @Mock
    private MarketDataProvider marketDataProvider;

    @InjectMocks
    private AlphaVantageService service;

    @Test
    void getLatestPrice_returnsValue_whenProviderHasPrice() {
    	
    	logger.info("=== Executing test: getLatestPrice_returnsValue_whenProviderHasPrice");

    	
        String symbol = "AAPL";
        BigDecimal price = new BigDecimal("150.25");
        when(marketDataProvider.getLatest(symbol)).thenReturn(Optional.of(price));

        BigDecimal result = service.getLatestStockPrice(symbol);

        //assertThat(result).
        assertThat(price).isEqualByComparingTo("150.25");
        verify(marketDataProvider).getLatest(symbol);
    }
    
    
    @Test
    void throwsException_whenProviderEmpty() {
    	
    	logger.info("=== Executing test: throwsException_whenProviderEmpty");

    	String symbol = "CRCL";
    	
    	when(marketDataProvider.getLatest(symbol)).thenReturn(Optional.empty());
    	
    	assertThatThrownBy(() -> service.getLatestStockPrice(symbol))
    		.isInstanceOf(NoSuchElementException.class)
    		.hasMessage("No price found for symbol "+symbol);
    }
	
}
