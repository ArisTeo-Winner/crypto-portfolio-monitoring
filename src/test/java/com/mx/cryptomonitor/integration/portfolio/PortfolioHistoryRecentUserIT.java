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
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

/**
 * Scenario 3 — RECENT USER (&lt;7 DAYS).
 *
 * <p>User has exactly one BUY transaction placed 3 days ago. For range=ALL the engine must start
 * the chart at (or very near) that transaction date — NOT months before it.
 *
 * <p>Assertions:
 *
 * <ol>
 *   <li>First timestamp &ge; transactionDate - 1 day
 *   <li>No months of leading zeros before the first real position
 *   <li>Series length reasonable (&lt; 100 points for a 3-day window)
 *   <li>Value evolution realistic (monotonically non-decreasing with increasing mock prices)
 * </ol>
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
@ActiveProfiles("test")
class PortfolioHistoryRecentUserIT {

  private static final String BTC = "BTC";
  private static final BigDecimal BUY_QUANTITY = new BigDecimal("1.0");
  private static final BigDecimal BUY_PRICE = new BigDecimal("60000.00");
  private static final int DAYS_AGO = 3;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;

  @MockBean private MarketPriceHistoryPort marketPriceHistoryPort;

  private User testUser;
  private LocalDateTime txDate;

  @BeforeEach
  void setUp() {
    String id = UUID.randomUUID().toString().substring(0, 8);
    testUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("recent-" + id)
                .email("recent-" + id + "@test.local")
                .passwordHash("hash")
                .build());

    txDate =
        LocalDateTime.now(ZoneOffset.UTC)
            .minusDays(DAYS_AGO)
            .withHour(9)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);

    transactionRepository.saveAndFlush(btcBuy(txDate, BUY_QUANTITY, BUY_PRICE));

    // Provide hourly prices for the past 30 days so the mock never returns an empty list,
    // regardless of which ChartResolution the engine selects for a 3-day window.
    List<PricePoint> prices =
        generateHourlyPrices(Instant.now().minus(Duration.ofDays(30)), Instant.now(), BUY_PRICE);

    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq(BTC), any(ChartResolution.class)))
        .thenReturn(prices);
  }

  @Test
  void rangeAll_firstTimestampIsNotBeforeTransactionDate() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    assertThat(series).as("Series must not be empty").isNotEmpty();

    long firstTime = series.get(0).get("time").asLong();
    long txEpoch = txDate.toInstant(ZoneOffset.UTC).getEpochSecond();
    long oneDaySeconds = 86_400L;

    assertThat(firstTime)
        .as(
            "First point epoch %d must be >= transactionDate epoch %d minus 1 day. "
                + "If series starts months back, the ALL-range anchor is broken.",
            firstTime, txEpoch)
        .isGreaterThanOrEqualTo(txEpoch - oneDaySeconds);
  }

  @Test
  void rangeAll_noMonthsOfLeadingZerosBeforeTransaction() throws Exception {
    List<JsonNode> series = callHistory("ALL");

    long txEpoch = txDate.toInstant(ZoneOffset.UTC).getEpochSecond();
    long oneDaySeconds = 86_400L;

    long zeroPrefixCountBeforeTx =
        series.stream()
            .filter(n -> n.get("time").asLong() < txEpoch - oneDaySeconds)
            .filter(n -> n.get("value").decimalValue().compareTo(BigDecimal.ZERO) == 0)
            .count();

    assertThat(zeroPrefixCountBeforeTx)
        .as(
            "Found %d zero-value points more than 1 day BEFORE the first transaction. "
                + "For a 3-day-old user this indicates months of spurious back-fill.",
            zeroPrefixCountBeforeTx)
        .isZero();
  }

  @Test
  void rangeAll_seriesLengthIsReasonable() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    // A 3-day window — even at intraday resolution (1h = 72 points) — cannot produce 100+ points
    // unless the engine is spuriously extending the range far into the past.
    assertThat(series.size())
        .as(
            "3-day-old user ALL range must yield < 100 points (got %d). "
                + "If exceeded, the chart window is wrongly extended.",
            series.size())
        .isLessThan(100);
  }

  @Test
  void rangeAll_valueEvolutionIsRealistic() throws Exception {
    List<JsonNode> series = callHistory("ALL");

    List<BigDecimal> values =
        series.stream()
            .map(n -> n.get("value").decimalValue())
            .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
            .toList();

    if (values.size() < 2) {
      return; // not enough data to verify trend
    }

    // With increasing mock prices and a constant BUY quantity, values must be non-decreasing.
    for (int i = 1; i < values.size(); i++) {
      assertThat(values.get(i))
          .as(
              "Value at index %d (%s) must be >= previous (%s) — no random spikes expected",
              i, values.get(i), values.get(i - 1))
          .isGreaterThanOrEqualTo(values.get(i - 1));
    }
  }

  @Test
  void rangeAll_noNegativeValues() throws Exception {
    List<JsonNode> series = callHistory("ALL");
    series.forEach(
        node -> {
          BigDecimal value = node.get("value").decimalValue();
          assertThat(value)
              .as("Value must be non-negative for a user with only BUY transactions")
              .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
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

  /**
   * Generates sub-daily (hourly) price points so the engine always receives data for small time
   * windows (e.g. the 3-day range that ChartResolutionStrategy selects as 15m or 1h intervals).
   */
  private List<PricePoint> generateHourlyPrices(Instant from, Instant to, BigDecimal startPrice) {
    List<PricePoint> points = new ArrayList<>();
    Instant current = from.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
    BigDecimal price = startPrice;
    BigDecimal multiplier = new BigDecimal("1.00004"); // ~0.1% per day at hourly cadence
    while (!current.isAfter(to)) {
      points.add(new PricePoint(current, price.setScale(2, RoundingMode.HALF_UP)));
      price = price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
      current = current.plus(Duration.ofHours(1));
    }
    return points;
  }
}
