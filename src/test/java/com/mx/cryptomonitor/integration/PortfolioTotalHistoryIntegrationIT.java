package com.mx.cryptomonitor.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

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
import com.jayway.jsonpath.JsonPath;
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
    // Forzar CoinGecko (stubeado) como ÚNICO proveedor CRYPTO: apagar los de spot/futuros en vivo
    // (@Order 0,1,2), que si no golpean Binance real y hacen el test dependiente de precios en
    // vivo.
    registry.add("external.providers.binance.enabled", () -> "false");
    registry.add("external.providers.bybit.enabled", () -> "false");
    registry.add("external.providers.binance.futures.enabled", () -> "false");
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
    // La compra es previa a los precios stubeados para que la cantidad ya aplique en el primer
    // punto.
    OffsetDateTime buyDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
    transactionRepository.saveAndFlush(transaction(firstUser, "BTC", new BigDecimal("1"), buyDate));
    transactionRepository.saveAndFlush(
        transaction(secondUser, "BTC", new BigDecimal("2"), buyDate));

    // El CoinGeckoMarketPriceHistoryAdapter llama /coins/{id}/market_chart/range (id = "BTC"); el
    // path anterior (/coins/bitcoin/market_chart) nunca matcheaba y el test dependia de Binance.
    // El historial TOTAL filtra los precios a la ventana [now-range, now], asi que los timestamps
    // se generan relativos a now (ultimos dias) en vez de fechas fijas que caerian fuera del rango.
    long dayMs = 86_400_000L;
    long nowMs = System.currentTimeMillis();
    String pricesBody =
        String.format(
            java.util.Locale.ROOT,
            "{\"prices\":[[%d,100.00],[%d,110.00]]}",
            nowMs - 3 * dayMs,
            nowMs - dayMs);
    coinGecko.stubFor(
        WireMock.get(urlPathEqualTo("/coins/BTC/market_chart/range"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(pricesBody)));

    String firstBody =
        mockMvc
            .perform(
                get("/api/v1/me/portfolio/history")
                    .param("range", "30d")
                    .param("assetTypes", "CRYPTO")
                    .with(authentication(authToken(firstUser))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String secondBody =
        mockMvc
            .perform(
                get("/api/v1/me/portfolio/history")
                    .param("range", "30d")
                    .param("assetTypes", "CRYPTO")
                    .with(authentication(authToken(secondUser))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Number firstInvested = JsonPath.read(firstBody, "$.meta.returns.totalInvested");
    Number secondInvested = JsonPath.read(secondBody, "$.meta.returns.totalInvested");
    Number firstValue = JsonPath.read(firstBody, "$.series[0].value");
    Number secondValue = JsonPath.read(secondBody, "$.series[0].value");

    assertThat(firstInvested.doubleValue()).isEqualTo(100.0);
    assertThat(secondInvested.doubleValue()).isEqualTo(200.0);
    assertThat(firstValue.doubleValue()).isPositive();
    assertThat(secondValue.doubleValue()).isCloseTo(firstValue.doubleValue() * 2, offset(0.01));
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
      User user, String symbol, BigDecimal quantity, OffsetDateTime time) {
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
