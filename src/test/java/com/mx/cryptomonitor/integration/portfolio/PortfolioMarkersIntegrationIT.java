package com.mx.cryptomonitor.integration.portfolio;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hamcrest.Matchers;
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
class PortfolioMarkersIntegrationIT extends InfraIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;

  private User firstUser;
  private User secondUser;

  @DynamicPropertySource
  static void registerMarkerProperties(DynamicPropertyRegistry registry) {
    registry.add("external.providers.coingecko.enabled", () -> "false");
    registry.add("external.providers.coingecko.base-url", () -> "http://localhost");
    registry.add("security.portfolio-history-rate-limit.enabled", () -> "false");
  }

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAll();
    userRepository.deleteAll();

    firstUser = user("markers-user-a");
    secondUser = user("markers-user-b");
  }

  @Test
  void returnsLightweightChartMarkersFromUserTransactionsOnly() throws Exception {
    Instant base =
        Instant.now().minus(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    transactionRepository.saveAndFlush(
        transaction(firstUser, "BTC", AssetType.CRYPTO, "BUY", "1.0", base));
    transactionRepository.saveAndFlush(
        transaction(firstUser, "BTC", AssetType.CRYPTO, "SELL", "0.5", base.plusSeconds(60)));
    transactionRepository.saveAndFlush(
        transaction(firstUser, "AAPL", AssetType.STOCK, "BUY", "3.0", base.plusSeconds(120)));
    transactionRepository.saveAndFlush(
        transaction(secondUser, "BTC", AssetType.CRYPTO, "BUY", "99.0", base));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/markers")
                .param("range", "30d")
                .param("assetTypes", "CRYPTO,STOCK")
                .with(authentication(authToken(firstUser))))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", Matchers.hasSize(3)))
        .andExpect(jsonPath("$[0].time").value(base.getEpochSecond()))
        .andExpect(jsonPath("$[0].position").value("belowBar"))
        .andExpect(jsonPath("$[0].color").value("#22c55e"))
        .andExpect(jsonPath("$[0].shape").value("arrowUp"))
        .andExpect(jsonPath("$[0].text").value("BUY 1 BTC"))
        .andExpect(jsonPath("$[1].time").value(base.plusSeconds(60).getEpochSecond()))
        .andExpect(jsonPath("$[1].position").value("aboveBar"))
        .andExpect(jsonPath("$[1].color").value("#ef4444"))
        .andExpect(jsonPath("$[1].shape").value("arrowDown"))
        .andExpect(jsonPath("$[1].text").value("SELL 0.5 BTC"))
        .andExpect(jsonPath("$[2].time").value(base.plusSeconds(120).getEpochSecond()))
        .andExpect(jsonPath("$[2].text").value("BUY 3 AAPL"))
        .andExpect(jsonPath("$[?(@.text=='BUY 99 BTC')]").isEmpty());
  }

  @Test
  void rejectsMarkersRequestWhenPrincipalDoesNotHaveUserRole() throws Exception {
    TestingAuthenticationToken adminOnly =
        new TestingAuthenticationToken(firstUser.getEmail(), null, "ROLE_ADMIN");
    adminOnly.setAuthenticated(true);

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/markers")
                .param("range", "30d")
                .with(authentication(adminOnly)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void rejectsInvalidAssetTypes() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/markers")
                .param("range", "30d")
                .param("assetTypes", "CRYPTO,BOND")
                .with(authentication(authToken(firstUser))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("PORTFOLIO_INVALID_REQUEST"));
  }

  @Test
  void rejectsInvalidRange() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/markers")
                .param("range", "999d")
                .with(authentication(authToken(firstUser))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("PORTFOLIO_INVALID_REQUEST"));
  }

  @Test
  void preservesSameTimestampOrderUsingTransactionRepositoryOrder() throws Exception {
    Instant transactionTime =
        Instant.now().minus(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    transactionRepository.saveAndFlush(
        transaction(
            firstUser,
            "BTC",
            AssetType.CRYPTO,
            "BUY",
            "1.0",
            transactionTime,
            transactionTime.plusSeconds(1)));
    transactionRepository.saveAndFlush(
        transaction(
            firstUser,
            "ETH",
            AssetType.CRYPTO,
            "SELL",
            "2.0",
            transactionTime,
            transactionTime.plusSeconds(2)));
    transactionRepository.saveAndFlush(
        transaction(
            firstUser,
            "SOL",
            AssetType.CRYPTO,
            "BUY",
            "3.0",
            transactionTime,
            transactionTime.plusSeconds(3)));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/markers")
                .param("range", "30d")
                .param("assetTypes", "CRYPTO")
                .with(authentication(authToken(firstUser))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(3)))
        .andExpect(jsonPath("$[0].time").value(transactionTime.getEpochSecond()))
        .andExpect(jsonPath("$[0].text").value("BUY 1 BTC"))
        .andExpect(jsonPath("$[1].time").value(transactionTime.getEpochSecond()))
        .andExpect(jsonPath("$[1].text").value("SELL 2 ETH"))
        .andExpect(jsonPath("$[2].time").value(transactionTime.getEpochSecond()))
        .andExpect(jsonPath("$[2].text").value("BUY 3 SOL"));
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
      User user, String symbol, AssetType assetType, String type, String quantity, Instant time) {
    return transaction(user, symbol, assetType, type, quantity, time, time);
  }

  private Transaction transaction(
      User user,
      String symbol,
      AssetType assetType,
      String type,
      String quantity,
      Instant time,
      Instant createdAt) {
    BigDecimal quantityValue = new BigDecimal(quantity);
    BigDecimal price = new BigDecimal("100.00");
    OffsetDateTime timestamp = OffsetDateTime.ofInstant(time, ZoneOffset.UTC);
    OffsetDateTime createdTimestamp = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC);
    return Transaction.builder()
        .user(user)
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol(symbol)
        .assetType(assetType)
        .transactionType(type)
        .quantity(quantityValue)
        .pricePerUnit(price)
        .totalValue(quantityValue.multiply(price))
        .transactionDate(timestamp)
        .fee(BigDecimal.ZERO)
        .createdAt(createdTimestamp)
        .updatedAt(timestamp)
        .build();
  }
}
