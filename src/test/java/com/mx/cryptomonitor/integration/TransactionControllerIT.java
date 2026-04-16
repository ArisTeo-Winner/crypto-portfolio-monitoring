package com.mx.cryptomonitor.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PortfolioEntryRepository portfolioEntryRepository;
  @Autowired private ObjectMapper objectMapper;

  private User user;
  private PortfolioEntry portfolioEntry;
  private String assetSymbol = "BTC";

  @BeforeEach
  void setUp() {
    user = new User();
    user.setUsername("tester");
    user.setEmail("test@gmail.com");
    user.setPasswordHash("password123");
    user.setFirstName("test");

    userRepository.save(user);

    portfolioEntry = new PortfolioEntry();
    portfolioEntry.setUserId(user.getId());
    portfolioEntry.setAssetSymbol(assetSymbol);
    portfolioEntry.setAssetType("CRYPTO");
    portfolioEntryRepository.save(portfolioEntry);

    Transaction transactionTest = new Transaction();
    transactionTest.setUser(user);
    transactionTest.setPortfolioEntryId(portfolioEntry.getPortfolioEntryId());
    transactionTest.setAssetSymbol(assetSymbol);
    transactionTest.setAssetType(AssetType.CRYPTO);
    transactionTest.setTransactionType("BUY");
    transactionTest.setQuantity(new BigDecimal("1.5"));
    transactionTest.setPricePerUnit(new BigDecimal("50000.00"));
    transactionTest.setTotalValue(new BigDecimal("75000.00"));

    transactionRepository.save(transactionTest);
  }

  @Test
  @WithMockUser(roles = "USER")
  void testGetTransactionsByUserAndSymbol() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/transactions/{userId}/{assetSymbol}", user.getId(), assetSymbol)
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
