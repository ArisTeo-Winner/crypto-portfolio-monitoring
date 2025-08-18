package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.validator.cfg.defs.DigitsDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerIT {
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private TransactionRepository transactionRepository;
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private PortfolioEntryRepository portfolioEntryRepository;
	
	@Autowired
	private ObjectMapper objectMapper; 
	
	private User user;
	
	private PortfolioEntry portfolioEntry;
	
	private UUID userId;
	private String assetSymbol = "BTC";

	@BeforeEach
	void setUp(){
		userId = UUID.randomUUID();
		
		System.out.println("VALOR :"+userId);
		user = new User();
		//user.setId(userId);
		user.setUsername("tester");
		user.setEmail("test@gmail.com");
		user.setPasswordHash("password123");
		user.setFirstName("test");
		
		userRepository.save(user);
		
		portfolioEntry = new PortfolioEntry();
		//portfolioEntry.setPortfolioEntryId(UUID.randomUUID());
		portfolioEntry.setUser(user);
		portfolioEntry.setAssetSymbol(assetSymbol);
		portfolioEntry.setAssetType("CRYPTO");
		
		portfolioEntryRepository.save(portfolioEntry);
		
		Transaction transactionTest = new Transaction();
		transactionTest.setUser(user);
		transactionTest.setPortfolioEntry(portfolioEntry);
		transactionTest.setAssetSymbol(assetSymbol);
		transactionTest.setAssetType("CRYPTO");
		transactionTest.setTransactionType("BUY");
		transactionTest.setQuantity(new BigDecimal(1.5));
		transactionTest.setPricePerUnit(new BigDecimal(50000.00));
		transactionTest.setTotalValue(new BigDecimal(75000.00));
		
		transactionRepository.save(transactionTest);
		
	}

    @Test
    @WithMockUser(roles = "USER")
    void testGetTransactionsByUserAndSymbol() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{userId}/{assetSymbol}", user.getId(), assetSymbol)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].assetSymbol").value("BTC"))
                .andExpect(jsonPath("$[0].transactionType").value("BUY"))
                .andExpect(jsonPath("$[0].quantity").value(1.5))
                .andExpect(jsonPath("$[0].pricePerUnit").value(50000.00))
                .andExpect(jsonPath("$[0].totalValue").value(75000.00));
    }
}
