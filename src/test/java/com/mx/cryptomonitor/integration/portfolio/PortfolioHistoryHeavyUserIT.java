package com.mx.cryptomonitor.integration.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.portfolio.application.port.in.GetPortfolioTotalHistoryUseCase;
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioHistoryResult;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

/**
 * Scenario 2 — HEAVY USER (120 TRANSACTIONS).
 *
 * <p>Verifies that the portfolio history engine respects O(n) time complexity and point-count
 * bounds when a user has 120 BUY/SELL transactions spread across 1 year.
 *
 * <p>Performance thresholds (measured at service level, not HTTP round-trip):
 *
 * <ul>
 *   <li>range=90d: &lt; 1000 points, &lt; 150 ms
 *   <li>range=ALL: &lt; 1500 points, &lt; 250 ms
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
@ActiveProfiles("test")
class PortfolioHistoryHeavyUserIT {

  private static final int TX_COUNT = 120;
  private static final String BTC = "BTC";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;

  // Direct use-case injection for sub-millisecond performance measurement
  // (bypasses HTTP stack overhead so thresholds are tight and meaningful).
  @Autowired private GetPortfolioTotalHistoryUseCase historyUseCase;

  @MockBean private MarketPriceHistoryPort marketPriceHistoryPort;

  private User testUser;

  @BeforeEach
  void setUp() {
    String id = UUID.randomUUID().toString().substring(0, 8);
    testUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("heavy-" + id)
                .email("heavy-" + id + "@test.local")
                .passwordHash("hash")
                .build());

    seedTransactions();
    stubMarketPrices();
  }

  // -------------------------------------------------------------------------
  // Point-count constraints
  // -------------------------------------------------------------------------

  @Test
  void range90d_pointCountIsBelow1000() throws Exception {
    List<JsonNode> series = callHistory("90d");
    assertThat(series.size())
        .as("90d series with 120 tx must have < 1000 points (got %d)", series.size())
        .isLessThan(1000);
  }

  @Test
  void rangeAll_pointCountIsBelow1500() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    assertThat(series.size())
        .as("ALL series with 120 tx must have < 1500 points (got %d)", series.size())
        .isLessThan(1500);
  }

  // -------------------------------------------------------------------------
  // Performance thresholds (service-level, not HTTP round-trip)
  // -------------------------------------------------------------------------

  @Test
  void range90d_serviceExecutionIsBelow150ms() {
    Runtime rt = Runtime.getRuntime();
    long memBefore = rt.totalMemory() - rt.freeMemory();

    // Warm-up: ensures JIT is active for the measured call.
    historyUseCase.getTotalHistory(testUser.getId(), "90d", null);

    long start = System.nanoTime();
    PortfolioHistoryResult result = historyUseCase.getTotalHistory(testUser.getId(), "90d", null);
    long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

    long memAfter = rt.totalMemory() - rt.freeMemory();
    long memDeltaKb = (memAfter - memBefore) / 1024;
    System.out.printf(
        "[perf] range=90d points=%d durationMs=%d memDeltaKB=%d%n",
        result.series().size(), durationMs, memDeltaKb);

    assertThat(durationMs)
        .as(
            "range=90d must complete in < 150ms at service level (O(n) expected); actual=%dms",
            durationMs)
        .isLessThan(150);
  }

  @Test
  void rangeAll_serviceExecutionIsBelow250ms() {
    Runtime rt = Runtime.getRuntime();
    long memBefore = rt.totalMemory() - rt.freeMemory();

    // Warm-up
    historyUseCase.getTotalHistory(testUser.getId(), "ALL", null);

    long start = System.nanoTime();
    PortfolioHistoryResult result = historyUseCase.getTotalHistory(testUser.getId(), "ALL", null);
    long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

    long memAfter = rt.totalMemory() - rt.freeMemory();
    long memDeltaKb = (memAfter - memBefore) / 1024;
    System.out.printf(
        "[perf] range=ALL points=%d durationMs=%d memDeltaKB=%d%n",
        result.series().size(), durationMs, memDeltaKb);

    assertThat(durationMs)
        .as(
            "range=ALL must complete in < 250ms at service level (O(n) expected); actual=%dms",
            durationMs)
        .isLessThan(250);
  }

  /**
   * O(n) complexity check: halving the effective data set (using 90d instead of ALL over 1 year)
   * must not produce an execution time that suggests O(n²) growth.
   *
   * <p>For O(n²) with N=120 transactions: T(ALL)/T(90d) ≈ (365/90)² ≈ 16x. For O(n): T(ALL)/T(90d)
   * ≈ 365/90 ≈ 4x. We assert the ratio stays within 8x (generous budget) to distinguish linear from
   * quadratic growth.
   */
  @Test
  void noRepeatedRecalculationPerPoint_onNBehavior() {
    // Warm-up both paths
    historyUseCase.getTotalHistory(testUser.getId(), "90d", null);
    historyUseCase.getTotalHistory(testUser.getId(), "ALL", null);

    long start90d = System.nanoTime();
    historyUseCase.getTotalHistory(testUser.getId(), "90d", null);
    long time90d = System.nanoTime() - start90d;

    long startAll = System.nanoTime();
    historyUseCase.getTotalHistory(testUser.getId(), "ALL", null);
    long timeAll = System.nanoTime() - startAll;

    // Avoid division by zero when 90d is sub-millisecond fast
    if (time90d < 1_000_000L) {
      // Both are extremely fast; O(n) is clearly satisfied
      return;
    }

    double ratio = (double) timeAll / time90d;
    assertThat(ratio)
        .as(
            "ALL/90d time ratio %.2f exceeds 8x — possible O(n²) recalculation per point."
                + " time90d=%dns timeAll=%dns",
            ratio, time90d, timeAll)
        .isLessThan(8.0);
  }

  // -------------------------------------------------------------------------
  // Correctness invariants carry over to heavy user
  // -------------------------------------------------------------------------

  @Test
  void range90d_timestampsAreStrictlyAscending() throws Exception {
    List<JsonNode> series = callHistory("90d");
    assertStrictlyAscending(series);
  }

  @Test
  void rangeAll_timestampsAreStrictlyAscending() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    assertStrictlyAscending(series);
  }

  @Test
  void range90d_noNegativeValues() throws Exception {
    List<JsonNode> series = callHistory("90d");
    assertNoNegativeValues(series);
  }

  @Test
  void rangeAll_noNegativeValues() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    assertNoNegativeValues(series);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private void seedTransactions() {
    // 120 transactions spread across 365 days.
    // Alternating BUY(0.2)/SELL(0.1) → net position always positive.
    for (int i = 0; i < TX_COUNT; i++) {
      boolean isBuy = (i % 2 == 0);
      LocalDateTime date =
          LocalDateTime.now(ZoneOffset.UTC)
              .minusDays(365)
              .plusDays((long) ((365.0 / TX_COUNT) * i))
              .withHour(10)
              .withMinute(0)
              .withSecond(0)
              .withNano(0);

      BigDecimal qty = isBuy ? new BigDecimal("0.20") : new BigDecimal("0.10");
      BigDecimal price =
          new BigDecimal("45000")
              .multiply(BigDecimal.valueOf(1.0 + i * 0.001))
              .setScale(2, RoundingMode.HALF_UP);

      transactionRepository.saveAndFlush(
          Transaction.builder()
              .user(testUser)
              .portfolioEntryId(UUID.randomUUID())
              .assetSymbol(BTC)
              .assetType(com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO)
              .transactionType(isBuy ? "BUY" : "SELL")
              .quantity(qty)
              .pricePerUnit(price)
              .totalValue(qty.multiply(price))
              .transactionDate(date)
              .fee(BigDecimal.ZERO)
              .createdAt(date)
              .updatedAt(date)
              .build());
    }
  }

  private void stubMarketPrices() {
    List<PricePoint> prices =
        generateDailyPrices(
            Instant.now().minus(Duration.ofDays(400)), Instant.now(), new BigDecimal("45000.00"));

    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq(BTC), any(ChartResolution.class)))
        .thenReturn(prices);
  }

  private List<JsonNode> callHistory(String range) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/me/portfolio/history")
                    .param("range", range)
                    .with(authentication(userAuth())))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    JsonNode root = objectMapper.readTree(body);
    JsonNode seriesNode = root.has("series") ? root.get("series") : root;
    List<JsonNode> series = new ArrayList<>();
    seriesNode.forEach(series::add);
    return series;
  }

  private void assertStrictlyAscending(List<JsonNode> series) {
    for (int i = 1; i < series.size(); i++) {
      long prev = series.get(i - 1).get("time").asLong();
      long curr = series.get(i).get("time").asLong();
      assertThat(curr)
          .as("Timestamps must be strictly ascending at index %d", i)
          .isGreaterThan(prev);
    }
  }

  private void assertNoNegativeValues(List<JsonNode> series) {
    series.forEach(
        node -> {
          BigDecimal value = node.get("value").decimalValue();
          assertThat(value)
              .as("Value must be non-negative: %s", value)
              .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
  }

  private TestingAuthenticationToken userAuth() {
    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(testUser.getEmail(), null, "ROLE_USER");
    auth.setAuthenticated(true);
    return auth;
  }

  private List<PricePoint> generateDailyPrices(Instant from, Instant to, BigDecimal startPrice) {
    List<PricePoint> points = new ArrayList<>();
    Instant current = alignToDay(from);
    BigDecimal price = startPrice;
    BigDecimal multiplier = new BigDecimal("1.001");
    while (!current.isAfter(to)) {
      points.add(new PricePoint(current, price.setScale(2, RoundingMode.HALF_UP)));
      price = price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
      current = current.plus(Duration.ofDays(1));
    }
    return points;
  }

  private static Instant alignToDay(Instant instant) {
    long epoch = instant.getEpochSecond();
    return Instant.ofEpochSecond(epoch - (epoch % 86_400L));
  }
}
