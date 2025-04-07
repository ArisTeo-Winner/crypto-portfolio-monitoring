package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@DataJpaTest
@ActiveProfiles("test")
@Rollback(false)
class PortfolioServiceIntegrationTest {
	
    private final Logger logger = LoggerFactory.getLogger(PortfolioServiceIntegrationTest.class);


	    @Autowired
	    private TransactionRepository transactionRepository;

	    @Autowired
	    private PortfolioEntryRepository portfolioEntryRepository;
	    
	    @Autowired
	    private UserRepository userRepository;
	    /*	*/
	    private User user;
	    
	    private UUID userId;

	    
	    @BeforeEach
	    public void setUp() {
	    	user = new User();
	    	user.setUsername("testUser");
	    	user.setEmail("testuser@example.com");
	    	user.setPasswordHash("hashedpassword");
	    	userRepository.save(user);
	    	
	    }
	    
	    @Test
	    void testSaveTransactionAndPortfolioEntry() {
	        //UUID userId = UUID.randomUUID();
	        //String uuidString = "9cd1ba3b-3676-4e52-8373-c7cd68492c71";
	        
	         userId = user.getId();
	        User user = userRepository.findById(userId)
	        		.orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
	        // 🔹 NO generar un UUID manualmente para PortfolioEntry
	        
	        
	        
	        logger.info("Cosulta un UUID de user: "+user);
	        
	        PortfolioEntry portfolioEntry = PortfolioEntry.builder()
	                .user(user)
	                .assetSymbol("ETH")
	                .assetType("CRYPTO")
	                .totalQuantity(BigDecimal.valueOf(2.0))
	                .totalInvested(BigDecimal.valueOf(5000))
	                .averagePricePerUnit(BigDecimal.valueOf(2500))
	                .lastTransactionPrice(null)
	                .currentValue(null)
	                .totalProfitLoss(null)
	                .lastUpdated(LocalDateTime.now())
	                .updatedAt(LocalDateTime.now())
	                .build();

	        portfolioEntry = portfolioEntryRepository.save(portfolioEntry); // 🔹 Guardar primero el PortfolioEntry

	        assertNotEquals(portfolioEntry.getUser(), "El usuario en PortfolioEntry es NULL");
	        
	        logger.info("ID generado por JPA: "+portfolioEntry.getUser().getId());
	        // Ahora el portfolioEntry tiene un ID generado por JPA
	        Transaction transaction = Transaction.builder()
	                .transactionId(UUID.randomUUID())
	                .user(user)
	                .portfolioEntry(portfolioEntry)  // 🔹 Ahora se asigna correctamente el PortfolioEntry
	                .assetSymbol("ETH")
	                .assetType("CRYPTO")
	                .transactionType("BUY")
	                .quantity(BigDecimal.valueOf(2.0))
	                .pricePerUnit(BigDecimal.valueOf(2500))
	                .totalValue(BigDecimal.valueOf(5000))
	                .createdAt(LocalDateTime.now())
	                .updatedAt(LocalDateTime.now())
	                .build();
	        


	        assertNotNull(transaction.getUser(), "❌ ERROR: El usuario en Transaction es NULL");
	        assertNotNull(transaction.getUser().getId(), "❌ ERROR: El ID del usuario en Transaction es NULL");

	        
	        logger.info("🔍 Usuario en Transaction: " + transaction.getUser());
	        logger.info("🆔 ID del usuario en Transaction: " + transaction.getUser().getId());
	        
	        transactionRepository.save(transaction);

	        assertNotNull(transactionRepository.findById(transaction.getTransactionId()));
	        assertNotNull(portfolioEntryRepository.findByUserIdAndAssetSymbol(portfolioEntry.getUser().getId(), "ETH"));
	    }

	    @Test
	    void testByIdUserTransaction() {
	    	
	    }
	    
	    
}
