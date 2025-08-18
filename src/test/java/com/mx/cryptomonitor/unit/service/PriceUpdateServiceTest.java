package com.mx.cryptomonitor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.PortfolioService;
import com.mx.cryptomonitor.domain.services.PriceUpdateService;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;

@ExtendWith(MockitoExtension.class)
class PriceUpdateServiceTest {
	private static final Logger logger = LoggerFactory.getLogger(PriceUpdateServiceTest.class);


    @Mock
    private RestTemplate restTemplate;
	
    @Mock
	private MarketDataService marketDataService;
	

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PortfolioEntryRepository portfolioEntryRepository;

 
    @Mock
    private PortfolioService portfolioService;
    
    @InjectMocks
    private PriceUpdateService priceUpdateService; 
    
    private UUID existingUserId;


    @Test
    public void testUpdatePrices_success() {
        logger.info("=== Ejecutando método testUpdatePrices_success() desde PriceUpdateServiceTest ===");

    	
        UUID userId = UUID.randomUUID();
        
        User user = new User();
        user.setId(userId);
        user.setUsername("testUser");
        user.setEmail("test@example.com");

    	
        // Simula el comportamiento de getCryptoPrice() para ETH
        when(marketDataService.getCryptoPrice("ETH")).thenReturn(Optional.of(BigDecimal.valueOf(2300)));

        // Simula una entrada en el portafolio
        PortfolioEntry entry = PortfolioEntry.builder()
                .user(user)
                .assetSymbol("ETH")
                .assetType("CRYPTO")
                .totalQuantity(BigDecimal.valueOf(2.0))
                .totalInvested(BigDecimal.valueOf(5000))
                .averagePricePerUnit(BigDecimal.valueOf(2500))
                .lastUpdated(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(portfolioEntryRepository.findDistinctAssetSymbols()).thenReturn(List.of("ETH"));
        when(portfolioEntryRepository.findByAssetSymbol("ETH")).thenReturn(List.of(entry));

        // Act: Ejecuta el método bajo prueba
        priceUpdateService.updatePrices();
      
        ArgumentCaptor<PortfolioEntry> captor = ArgumentCaptor.forClass(PortfolioEntry.class);
        verify(portfolioEntryRepository).save(captor.capture());
        
        PortfolioEntry savedEntry = captor.getValue();
        
        logger.info("Valor almacenado: {}",savedEntry.getCurrentValue());
        
        assertNotNull(savedEntry);
        //assertEquals(BigDecimal.valueOf(4600).setScale(2), savedEntry.getCurrentValue().setScale(2)); // 2 * 2300
    }
}
