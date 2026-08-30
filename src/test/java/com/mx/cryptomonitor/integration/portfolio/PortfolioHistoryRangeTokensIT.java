package com.mx.cryptomonitor.integration.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePort;
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import reactor.core.publisher.Mono;

/**
 * Recorre el endpoint {@code GET /api/v1/me/portfolio/history} en los 7 tokens canónicos de rango
 * (1D/1S/1M/3M/6M/1Y/ALL) y verifica el round-trip HTTP y que la ventana pedida al proveedor es por
 * calendario (finitos = {@code now − periodo}, ALL = día de la primera transacción).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
@ActiveProfiles("test")
class PortfolioHistoryRangeTokensIT {

  private static final OffsetDateTime FIRST_BUY =
      OffsetDateTime.of(2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;
  @MockBean private AssetPricePort assetPricePort;
  @MockBean private CryptoHistoricalPricePort cryptoHistoricalPricePort;
  @MockBean private MarketPriceHistoryPort marketPriceHistoryPort;

  private User testUser;

  @BeforeEach
  void setup() {
    when(assetPricePort.getCryptoPriceAmount(anyString())).thenReturn(Mono.empty());

    transactionRepository.deleteAll();
    userRepository.deleteAll();

    testUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("range-tokens-" + UUID.randomUUID())
                .email("range-tokens-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build());
    transactionRepository.saveAndFlush(btcBuy(FIRST_BUY, "1", "95000"));

    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq("BTC"), any(ChartResolution.class)))
        .thenReturn(recentBtcPrices());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1D", "1S", "1M", "3M", "6M", "1Y", "ALL"})
  void everyCanonicalRangeRoundTrips(String token) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", token)
                .param("assetTypes", "CRYPTO")
                .with(authentication(userAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.range").value(token))
        .andExpect(jsonPath("$.meta.currency").value("USD"))
        .andExpect(jsonPath("$.meta.resolution").isNotEmpty())
        .andExpect(jsonPath("$.meta.points").isNumber())
        .andExpect(jsonPath("$.series").isArray());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1D", "1S", "1M", "3M", "6M", "1Y"})
  void finiteRangesRequestCalendarBoundedWindow(String token) throws Exception {
    Instant before = Instant.now();
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", token)
                .param("assetTypes", "CRYPTO")
                .with(authentication(userAuth())))
        .andExpect(status().isOk());
    Instant after = Instant.now();

    ChartResolution requested = capturedResolution();
    Period period = periodFor(token);
    Instant lowerBound = before.atZone(ZoneOffset.UTC).minus(period).toInstant();
    Instant upperBound = after.atZone(ZoneOffset.UTC).minus(period).toInstant();

    // start == now − periodo de calendario; robusto al desfase de reloj entre test y servicio.
    assertThat(requested.start()).isBetween(lowerBound, upperBound);
  }

  @Test
  void allRangeStartsAtFirstTransactionDay() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "ALL")
                .param("assetTypes", "CRYPTO")
                .with(authentication(userAuth())))
        .andExpect(status().isOk());

    ChartResolution requested = capturedResolution();
    // ALL deriva su inicio de la primera transacción (día UTC), no de un rango fijo.
    assertThat(requested.start()).isEqualTo(FIRST_BUY.toInstant());
  }

  private ChartResolution capturedResolution() {
    ArgumentCaptor<ChartResolution> captor = ArgumentCaptor.forClass(ChartResolution.class);
    verify(marketPriceHistoryPort, atLeastOnce())
        .getPriceHistory(eq(AssetType.CRYPTO), eq("BTC"), captor.capture());
    return captor.getValue();
  }

  private static Period periodFor(String token) {
    return switch (token) {
      case "1D" -> Period.ofDays(1);
      case "1S" -> Period.ofWeeks(1);
      case "1M" -> Period.ofMonths(1);
      case "3M" -> Period.ofMonths(3);
      case "6M" -> Period.ofMonths(6);
      case "1Y" -> Period.ofYears(1);
      default -> throw new IllegalArgumentException("token no finito: " + token);
    };
  }

  private List<PricePoint> recentBtcPrices() {
    Instant now = Instant.now();
    return List.of(
        priceAt(now.minus(Duration.ofHours(6)), "103000"),
        priceAt(now.minus(Duration.ofDays(3)), "101000"),
        priceAt(now.minus(Duration.ofDays(25)), "98000"),
        priceAt(now.minus(Duration.ofDays(100)), "90000"),
        priceAt(now.minus(Duration.ofDays(200)), "82000"),
        priceAt(now.minus(Duration.ofDays(300)), "95000"));
  }

  private PricePoint priceAt(Instant time, String value) {
    return new PricePoint(time, new BigDecimal(value));
  }

  private Transaction btcBuy(OffsetDateTime date, String qty, String price) {
    return Transaction.builder()
        .user(testUser)
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol("BTC")
        .assetType(com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO)
        .transactionType("BUY")
        .quantity(new BigDecimal(qty))
        .pricePerUnit(new BigDecimal(price))
        .totalValue(new BigDecimal(qty).multiply(new BigDecimal(price)))
        .transactionDate(date)
        .fee(BigDecimal.ZERO)
        .createdAt(date)
        .updatedAt(date)
        .build();
  }

  private TestingAuthenticationToken userAuth() {
    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(testUser.getEmail(), null, "ROLE_USER");
    auth.setAuthenticated(true);
    return auth;
  }
}
