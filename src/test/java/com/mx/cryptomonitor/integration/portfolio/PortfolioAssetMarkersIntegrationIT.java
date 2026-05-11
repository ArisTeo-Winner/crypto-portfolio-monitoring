package com.mx.cryptomonitor.integration.portfolio;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortfolioAssetMarkersIntegrationIT extends InfraIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;

  private User firstUser;
  private User secondUser;

  @DynamicPropertySource
  static void registerAssetMarkerProperties(DynamicPropertyRegistry registry) {
    registry.add("external.providers.coingecko.enabled", () -> "false");
    registry.add("external.providers.coingecko.base-url", () -> "http://localhost");
    registry.add("security.portfolio-history-rate-limit.enabled", () -> "true");
    registry.add("security.portfolio-history-rate-limit.max-attempts", () -> "20");
    registry.add("security.portfolio-history-rate-limit.window-seconds", () -> "60");
  }

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAll();
    userRepository.deleteAll();

    firstUser = user("asset-markers-a");
    secondUser = user("asset-markers-b");
  }

  @Test
  void returnsOnlyAuthenticatedUsersMarkersForRequestedAsset() throws Exception {
    Instant base =
        Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
    transactionRepository.saveAndFlush(
        transaction(firstUser, "SOL", AssetType.CRYPTO, "BUY", "2.0", "154.46000000", base));
    transactionRepository.saveAndFlush(
        transaction(
            firstUser,
            "SOL",
            AssetType.CRYPTO,
            "SELL",
            "0.5",
            "160.00000000",
            base.plusSeconds(60)));
    transactionRepository.saveAndFlush(
        transaction(
            firstUser,
            "BTC",
            AssetType.CRYPTO,
            "BUY",
            "1.0",
            "50000.00000000",
            base.plusSeconds(120)));
    transactionRepository.saveAndFlush(
        transaction(
            secondUser,
            "SOL",
            AssetType.CRYPTO,
            "BUY",
            "99.0",
            "100.00000000",
            base.plusSeconds(180)));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/assets/{symbol}/markers", "SOL")
                .param("range", "30d")
                .with(authentication(authToken(firstUser))))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].time").value(base.getEpochSecond()))
        .andExpect(jsonPath("$[0].position").value("belowBar"))
        .andExpect(jsonPath("$[0].color").value("#16a34a"))
        .andExpect(jsonPath("$[0].shape").value("arrowUp"))
        .andExpect(jsonPath("$[0].text").value("BUY 2 SOL @ 154.46"))
        .andExpect(jsonPath("$[1].time").value(base.plusSeconds(60).getEpochSecond()))
        .andExpect(jsonPath("$[1].position").value("aboveBar"))
        .andExpect(jsonPath("$[1].color").value("#ef4444"))
        .andExpect(jsonPath("$[1].shape").value("arrowDown"))
        .andExpect(jsonPath("$[1].text").value("SELL 0.5 SOL @ 160"))
        .andExpect(jsonPath("$[?(@.text=='BUY 1 BTC @ 50000')]").isEmpty())
        .andExpect(jsonPath("$[?(@.text=='BUY 99 SOL @ 100')]").isEmpty());
  }

  @Test
  void authenticatedUsersCannotSeeEachOthersAssetMarkers() throws Exception {
    Instant base =
        Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
    transactionRepository.saveAndFlush(
        transaction(firstUser, "SOL", AssetType.CRYPTO, "BUY", "2.0", "154.46000000", base));
    transactionRepository.saveAndFlush(
        transaction(secondUser, "SOL", AssetType.CRYPTO, "BUY", "4.0", "150.00000000", base));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/assets/{symbol}/markers", "SOL")
                .param("range", "30d")
                .with(authentication(authToken(secondUser))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].text").value("BUY 4 SOL @ 150"));
  }

  @Test
  void invalidRangeReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/assets/{symbol}/markers", "SOL")
                .param("range", "180")
                .with(authentication(authToken(firstUser))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("PORTFOLIO_INVALID_REQUEST"));
  }

  @Test
  void returnsEmptyListWhenNoAssetTransactionsExist() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/assets/{symbol}/markers", "SOL")
                .param("range", "30d")
                .with(authentication(authToken(firstUser))))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void rateLimiterAllowsRequestsWithinLimit() throws Exception {
    for (int attempt = 0; attempt < 3; attempt++) {
      mockMvc
          .perform(
              get("/api/v1/me/portfolio/assets/{symbol}/markers", "SOL")
                  .param("range", "30d")
                  .with(authentication(authToken(firstUser))))
          .andExpect(status().isOk());
    }
  }

  private User user(String username) {
    return userRepository.saveAndFlush(
        User.builder()
            .username(username + "-" + UUID.randomUUID())
            .email(username + "-" + UUID.randomUUID() + "@example.com")
            .passwordHash("hash")
            .build());
  }

  private TestingAuthenticationToken authToken(User user) {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(user.getEmail(), null, "ROLE_USER");
    authentication.setAuthenticated(true);
    return authentication;
  }

  private Transaction transaction(
      User user,
      String symbol,
      AssetType assetType,
      String type,
      String quantity,
      String price,
      Instant transactionTime) {
    BigDecimal quantityValue = new BigDecimal(quantity);
    BigDecimal priceValue = new BigDecimal(price);
    LocalDateTime timestamp = LocalDateTime.ofInstant(transactionTime, ZoneOffset.UTC);
    return Transaction.builder()
        .user(user)
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol(symbol)
        .assetType(assetType)
        .transactionType(type)
        .quantity(quantityValue)
        .pricePerUnit(priceValue)
        .totalValue(quantityValue.multiply(priceValue))
        .transactionDate(timestamp)
        .fee(BigDecimal.ZERO)
        .createdAt(timestamp)
        .updatedAt(timestamp)
        .build();
  }
}
