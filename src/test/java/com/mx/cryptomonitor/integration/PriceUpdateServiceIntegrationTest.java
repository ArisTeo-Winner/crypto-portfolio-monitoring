package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.PriceUpdateService;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;


@SpringBootTest
@ActiveProfiles("test")
class PriceUpdateServiceIntegrationTest {
	
	private static final Logger logger = LoggerFactory.getLogger(PriceUpdateServiceIntegrationTest.class);
	

    @Autowired
    private PriceUpdateService service;
    
    @Autowired
    private PortfolioEntryRepository repository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MarketDataService marketDataService;

    private final String SYMBOL_ETH = "ETH";

 
    @Test
    @Disabled
    public void testUpdatePrices_integration() {
    	
        logger.info("=== Ejecutando método testUpdatePrices_integration() desde PriceUpdateServiceIntegrationTest ===");

        UUID userId = UUID.randomUUID(); 
	    //UUID userId = UUID.fromString("e7c11796-b0dd-4170-b0c1-ff4807d50737");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testUser");
        user.setEmail("test@example.com");
        user.setPasswordHash("test@123");
        
        
        logger.info("Datos de usuario mapeado:{}",userRepository.save(user));
        
        // Arrange        
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
        
        entry.setTotalInvested(BigDecimal.valueOf(502.06));
                
        logger.info("Datos mapeado de PortfolioEntry",repository.save(entry));



        Optional<BigDecimal> priceOpt = marketDataService.getCryptoPrice(SYMBOL_ETH);
        BigDecimal currentPrice = priceOpt.get();

        // Act
        service.updatePrices();
        PortfolioEntry updatedEntry = repository.findByAssetSymbol(SYMBOL_ETH).get(0);
 
        logger.info("Valor actual del activo (calculado en tiempo real); {}: {} ",currentPrice,updatedEntry.getCurrentValue());
  
        // Assert
        //assertEquals(BigDecimal.valueOf(2162.59), updatedEntry.getCurrentValue());
        //assertEquals(BigDecimal.valueOf(-115.06), updatedEntry.getTotalProfitLoss());
        
       
    } /*
*/
}
