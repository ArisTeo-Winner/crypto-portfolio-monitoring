package com.mx.cryptomonitor.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class PortfolioTotalHistoryIntegrationIT extends InfraIntegrationTest {

  private static final WireMockServer coinGecko =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @Autowired private MockMvc mockMvc;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private UserRepository userRepository;

  private User firstUser;
  private User secondUser;

  @BeforeAll
  static void startWireMock() {
    coinGecko.start();
    WireMock.configureFor("localhost", coinGecko.port());
  }

  @AfterAll
  static void stopWireMock() {
    coinGecko.stop();
  }

  @DynamicPropertySource
  static void registerCoinGecko(DynamicPropertyRegistry registry) {
    registry.add("external.providers.coingecko.base-url", coinGecko::baseUrl);
    registry.add("external.providers.coingecko.enabled", () -> "true");
    registry.add("marketdata.alphavantage.base-url", () -> "http://localhost");
    registry.add("marketdata.alphavantage.api-key", () -> "demo");
    registry.add("polygon.base-url", () -> "");
    registry.add("polygon.api.key", () -> "");
  }

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAll();
    userRepository.deleteAll();
    coinGecko.resetAll();

    firstUser = saveUser("total-history-a");
    secondUser = saveUser("total-history-b");
  }

  @Test
  void totalHistorySharesRedisPriceCacheAndKeepsUserQuantitiesIsolated() throws Exception {
    LocalDateTime buyDate = LocalDateTime.of(2026, 1, 1, 0, 0);
    transactionRepository.saveAndFlush(transaction(firstUser, "BTC", new BigDecimal("1"), buyDate));
    transactionRepository.saveAndFlush(
        transaction(secondUser, "BTC", new BigDecimal("2"), buyDate));

    coinGecko.stubFor(
        WireMock.get(urlPathEqualTo("/coins/bitcoin/market_chart"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "prices": [
                            [1767225600000, 100.00],
                            [1767312000000, 110.00]
                          ]
                        }
                        """)));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "30d")
                .param("assetTypes", "CRYPTO")
                .with(authentication(authToken(firstUser))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0].value").value(100.00))
        .andExpect(jsonPath("$[1].value").value(110.00));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "30d")
                .param("assetTypes", "CRYPTO")
                .with(authentication(authToken(secondUser))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0].value").value(200.00))
        .andExpect(jsonPath("$[1].value").value(220.00));

    verify(1, getRequestedFor(urlPathEqualTo("/coins/bitcoin/market_chart")));
  }

  private User saveUser(String prefix) {
    return userRepository.saveAndFlush(
        User.builder()
            .username(prefix + "-" + UUID.randomUUID())
            .email(prefix + "-" + UUID.randomUUID() + "@example.com")
            .passwordHash("hash")
            .build());
  }

  private Transaction transaction(
      User user, String symbol, BigDecimal quantity, LocalDateTime time) {
    return Transaction.builder()
        .user(user)
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol(symbol)
        .assetType(AssetType.CRYPTO)
        .transactionType("BUY")
        .quantity(quantity)
        .pricePerUnit(new BigDecimal("100.00000000"))
        .totalValue(quantity.multiply(new BigDecimal("100.00")))
        .transactionDate(time)
        .fee(BigDecimal.ZERO)
        .createdAt(time)
        .updatedAt(time)
        .build();
  }

  private TestingAuthenticationToken authToken(User user) {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(user.getEmail(), null, "ROLE_USER");
    authentication.setAuthenticated(true);
    return authentication;
  }
}
