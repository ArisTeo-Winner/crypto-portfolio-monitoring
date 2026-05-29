package com.mx.cryptomonitor.integration.portfolio;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetHoldingsHistoryFlowIT extends InfraIntegrationTest {

  private static final WireMockServer coinGecko =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;

  @DynamicPropertySource
  static void registerHistoryProperties(DynamicPropertyRegistry registry) {
    if (!coinGecko.isRunning()) {
      coinGecko.start();
    }
    registry.add("external.providers.coingecko.base-url", coinGecko::baseUrl);
    registry.add("external.providers.coingecko.enabled", () -> "true");
    registry.add("security.portfolio-history-rate-limit.enabled", () -> "false");
  }

  @BeforeEach
  void setUp() {
    coinGecko.resetAll();
    transactionRepository.deleteAll();
    userRepository.deleteAll();

    coinGecko.stubFor(
        get(urlPathEqualTo("/coins/bitcoin/market_chart"))
            .withQueryParam("vs_currency", equalTo("usd"))
            .withQueryParam("days", equalTo("30"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                        {
                          "prices": [
                            [1767225600000, 100.00],
                            [1767312000000, 110.00],
                            [1767398400000, 120.00]
                          ]
                        }
                        """)));
  }

  @Test
  void shouldReconstructHoldingsFromTransactionsAndReuseSharedRedisPriceCurveAcrossUsers()
      throws Exception {
    User firstUser = user("history-user-1");
    User secondUser = user("history-user-2");

    transactionRepository.saveAndFlush(
        transaction(firstUser, "BTC", "BUY", new BigDecimal("1"), "2026-01-01T00:00:00"));
    transactionRepository.saveAndFlush(
        transaction(secondUser, "BTC", "BUY", new BigDecimal("2"), "2026-01-01T00:00:00"));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/assets/{symbol}/history", "BTC")
                .param("range", "30d")
                .with(authentication(userAuthentication(firstUser))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").value(100.00))
        .andExpect(jsonPath("$[2].value").value(120.00));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/assets/{symbol}/history", "BTC")
                .param("range", "30d")
                .with(authentication(userAuthentication(secondUser))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").value(200.00))
        .andExpect(jsonPath("$[2].value").value(240.00));

    coinGecko.verify(
        1,
        getRequestedFor(urlPathEqualTo("/coins/bitcoin/market_chart"))
            .withQueryParam("vs_currency", equalTo("usd"))
            .withQueryParam("days", equalTo("30")));
  }

  private User user(String username) {
    return userRepository.saveAndFlush(
        User.builder()
            .username(username)
            .email(username + "@example.com")
            .passwordHash("hash")
            .build());
  }

  private TestingAuthenticationToken userAuthentication(User user) {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(user.getEmail(), null, "ROLE_USER");
    authentication.setAuthenticated(true);
    return authentication;
  }

  private Transaction transaction(
      User user, String symbol, String type, BigDecimal quantity, String transactionDate) {
    OffsetDateTime timestamp =
        OffsetDateTime.parse(transactionDate + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    BigDecimal price = new BigDecimal("100.00");
    return Transaction.builder()
        .user(user)
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol(symbol)
        .assetType(com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO)
        .transactionType(type)
        .quantity(quantity)
        .pricePerUnit(price)
        .totalValue(quantity.multiply(price))
        .transactionDate(timestamp)
        .fee(BigDecimal.ZERO)
        .createdAt(timestamp)
        .updatedAt(timestamp)
        .build();
  }
}
