package com.mx.cryptomonitor.integration.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinGeckoMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

/**
 * Scenario 1 — SINGLE TRANSACTION USER.
 *
 * <p>Verifies full correctness of the history series when the portfolio contains exactly one BUY
 * transaction placed 60 days ago. Exercises ranges 90d and ALL.
 *
 * <p>Also covers: External-Provider Safety Check (CoinGecko must NOT be called for ALL range), JSON
 * contract validation, and resolution density bounds.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
@ActiveProfiles("test")
class PortfolioHistorySingleTransactionIT {

  private static final String BTC = "BTC";
  private static final BigDecimal BUY_QUANTITY = new BigDecimal("0.5");
  private static final BigDecimal BUY_PRICE = new BigDecimal("50000.00");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;

  @MockBean private MarketPriceHistoryPort marketPriceHistoryPort;

  // Safety canary: must NOT be invoked via the MarketPriceHistoryPort mock path.
  @SpyBean private CoinGeckoMarketPriceHistoryAdapter coinGeckoAdapter;

  private User testUser;
  private LocalDateTime txDate;

  @BeforeEach
  void setUp() {
    String id = UUID.randomUUID().toString().substring(0, 8);
    testUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("single-tx-" + id)
                .email("single-tx-" + id + "@test.local")
                .passwordHash("hash")
                .build());

    txDate =
        LocalDateTime.now(ZoneOffset.UTC)
            .minusDays(60)
            .withHour(12)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);

    transactionRepository.saveAndFlush(btcBuy(txDate, BUY_QUANTITY, BUY_PRICE));

    // Stub: return 400 daily BTC prices covering any chart window the service may request.
    List<PricePoint> syntheticPrices =
        generateDailyPrices(
            Instant.now().minus(Duration.ofDays(400)), Instant.now(), new BigDecimal("45000.00"));

    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq(BTC), any(ChartResolution.class)))
        .thenReturn(syntheticPrices);
  }

  // -------------------------------------------------------------------------
  // range=90d
  // -------------------------------------------------------------------------

  @Test
  void range90d_seriesIsNotEmpty() throws Exception {
    List<JsonNode> series = callHistory("90d");
    assertThat(series).as("Series for 90d must not be empty").isNotEmpty();
  }

  @Test
  void range90d_timestampsAreStrictlyAscending() throws Exception {
    List<JsonNode> series = callHistory("90d");
    for (int i = 1; i < series.size(); i++) {
      long prev = series.get(i - 1).get("time").asLong();
      long curr = series.get(i).get("time").asLong();
      assertThat(curr)
          .as(
              "Timestamps must be strictly ascending: index %d (%d) <= index %d (%d)",
              i - 1, prev, i, curr)
          .isGreaterThan(prev);
    }
  }

  @Test
  void range90d_noDuplicateTimestamps() throws Exception {
    List<JsonNode> series = callHistory("90d");
    long unique = series.stream().map(n -> n.get("time").asLong()).distinct().count();
    assertThat(unique).as("No duplicate timestamps in 90d series").isEqualTo(series.size());
  }

  @Test
  void range90d_noNegativeValues() throws Exception {
    List<JsonNode> series = callHistory("90d");
    series.forEach(
        node -> {
          BigDecimal value = node.get("value").decimalValue();
          assertThat(value)
              .as("Value must be non-negative: %s", value)
              .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
  }

  @Test
  void range90d_noArtificialMicroNoise() throws Exception {
    List<JsonNode> series = callHistory("90d");
    // With a constant BUY quantity and monotonically increasing mock prices,
    // non-zero portfolio values must also be monotonically non-decreasing.
    List<BigDecimal> nonZero =
        series.stream()
            .map(n -> n.get("value").decimalValue())
            .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
            .toList();
    for (int i = 1; i < nonZero.size(); i++) {
      assertThat(nonZero.get(i))
          .as("Non-zero value at index %d must be >= previous (no noise injection)", i)
          .isGreaterThanOrEqualTo(nonZero.get(i - 1));
    }
  }

  @Test
  void range90d_firstNonZeroTimestampIsNearTransactionDate() throws Exception {
    List<JsonNode> series = callHistory("90d");
    long txEpoch = txDate.toInstant(ZoneOffset.UTC).getEpochSecond();
    long oneDaySeconds = 86_400L;

    long firstNonZeroTime =
        series.stream()
            .filter(n -> n.get("value").decimalValue().compareTo(BigDecimal.ZERO) > 0)
            .mapToLong(n -> n.get("time").asLong())
            .findFirst()
            .orElseThrow(() -> new AssertionError("Series has no non-zero values for 90d range"));

    assertThat(Math.abs(firstNonZeroTime - txEpoch))
        .as(
            "First non-zero point (%d) must be within ±1 day of transaction date (%d)",
            firstNonZeroTime, txEpoch)
        .isLessThanOrEqualTo(oneDaySeconds);
  }

  @Test
  void range90d_noMassiveZeroPaddingBeforeFirstTransaction() throws Exception {
    List<JsonNode> series = callHistory("90d");
    long zeroPrefixCount =
        series.stream()
            .takeWhile(n -> n.get("value").decimalValue().compareTo(BigDecimal.ZERO) == 0)
            .count();
    // Chart starts 90 days ago; transaction is 60 days ago → at most ~30 daily zero points.
    // "Massive" means more than a full month of extra zeros beyond this expected window.
    assertThat(zeroPrefixCount)
        .as(
            "Zero-value prefix must not be massive (found %d zero points before first tx)",
            zeroPrefixCount)
        .isLessThanOrEqualTo(35);
  }

  @Test
  void range90d_pointCountIsWithinBounds() throws Exception {
    List<JsonNode> series = callHistory("90d");
    assertThat(series.size())
        .as("90d series must have < 1000 points (got %d)", series.size())
        .isLessThan(1000);
  }

  @Test
  void range90d_jsonContractIsValid() throws Exception {
    List<JsonNode> series = callHistory("90d");
    assertJsonContract(series);
  }

  // -------------------------------------------------------------------------
  // range=ALL
  // -------------------------------------------------------------------------

  @Test
  void rangeAll_seriesIsNotEmpty() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    assertThat(series).as("Series for ALL must not be empty").isNotEmpty();
  }

  @Test
  void rangeAll_timestampsAreStrictlyAscending() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    for (int i = 1; i < series.size(); i++) {
      long prev = series.get(i - 1).get("time").asLong();
      long curr = series.get(i).get("time").asLong();
      assertThat(curr)
          .as("Timestamps must be strictly ascending at index %d", i)
          .isGreaterThan(prev);
    }
  }

  @Test
  void rangeAll_noDuplicateTimestamps() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    long unique = series.stream().map(n -> n.get("time").asLong()).distinct().count();
    assertThat(unique).as("No duplicate timestamps in ALL series").isEqualTo(series.size());
  }

  @Test
  void rangeAll_noNegativeValues() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    series.forEach(
        node -> {
          BigDecimal value = node.get("value").decimalValue();
          assertThat(value)
              .as("Value must be non-negative: %s", value)
              .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
  }

  @Test
  void rangeAll_firstTimestampIsNearFirstTransactionDate() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    assertThat(series).isNotEmpty();

    long firstTime = series.get(0).get("time").asLong();
    long txEpoch = txDate.toInstant(ZoneOffset.UTC).getEpochSecond();
    long oneDaySeconds = 86_400L;

    assertThat(firstTime)
        .as("First point (%d) must be >= transactionDate (%d) - 1 day", firstTime, txEpoch)
        .isGreaterThanOrEqualTo(txEpoch - oneDaySeconds);
  }

  @Test
  void rangeAll_pointCountIsWithinBounds() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    assertThat(series.size())
        .as("ALL series must have < 1500 points (got %d)", series.size())
        .isLessThan(1500);
  }

  @Test
  void rangeAll_jsonContractIsValid() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    assertJsonContract(series);
  }

  /**
   * External Provider Safety Check — HARD constraint.
   *
   * <p>For range=ALL, the historical engine must NOT call any external market data adapter
   * directly. All market data must flow through the {@link MarketPriceHistoryPort} abstraction
   * (which is mocked here). If CoinGecko is called, the spy intercepts it and this test FAILS.
   */
  @Test
  void rangeAll_coinGeckoAdapterMustNotBeCalled() throws Exception {
    callHistory("ALL");
    verifyNoInteractions(coinGeckoAdapter);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

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

  private void assertJsonContract(List<JsonNode> series) {
    series.forEach(
        node -> {
          // time must be a JSON integer (no decimal, no exponent)
          assertThat(node.has("time")).as("Each point must have 'time' field").isTrue();
          assertThat(node.get("time").isIntegralNumber())
              .as("'time' must be an integer (epoch seconds), got: %s", node.get("time"))
              .isTrue();

          // value must not be null
          assertThat(node.has("value")).as("Each point must have 'value' field").isTrue();
          assertThat(node.get("value").isNull()).as("'value' must not be null").isFalse();

          // value scale must be <= 2 (monetary precision)
          BigDecimal value = node.get("value").decimalValue();
          assertThat(value.scale())
              .as("'value' scale must be <= 2, got scale %d for value %s", value.scale(), value)
              .isLessThanOrEqualTo(2);

          // No scientific notation: string representation must not contain 'E' or 'e'
          String raw = node.get("value").asText();
          assertThat(raw)
              .as("'value' must not use scientific notation: %s", raw)
              .doesNotContainIgnoringCase("e");
        });
  }

  private TestingAuthenticationToken userAuth() {
    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(testUser.getEmail(), null, "ROLE_USER");
    auth.setAuthenticated(true);
    return auth;
  }

  private Transaction btcBuy(LocalDateTime date, BigDecimal qty, BigDecimal price) {
    return Transaction.builder()
        .user(testUser)
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol(BTC)
        .assetType(com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO)
        .transactionType("BUY")
        .quantity(qty)
        .pricePerUnit(price)
        .totalValue(qty.multiply(price))
        .transactionDate(date)
        .fee(BigDecimal.ZERO)
        .createdAt(date)
        .updatedAt(date)
        .build();
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
