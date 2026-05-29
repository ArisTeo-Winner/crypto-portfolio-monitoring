package com.mx.cryptomonitor.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePort;
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
@ActiveProfiles("test")
class PortfolioControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PortfolioEntryRepository portfolioEntryRepository;
  @Autowired private TransactionRepository transactionRepository;
  @MockBean private AssetPricePort assetPricePort;
  @MockBean private CryptoHistoricalPricePort cryptoHistoricalPricePort;
  @MockBean private MarketPriceHistoryPort marketPriceHistoryPort;

  private User testUser;

  @BeforeEach
  void setup() {
    org.mockito.Mockito.when(
            assetPricePort.getCryptoPriceAmount(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Mono.empty());
    transactionRepository.deleteAll();
    portfolioEntryRepository.deleteAll();
    userRepository.deleteAll();

    testUser =
        User.builder()
            .username("portfolio-user-" + UUID.randomUUID())
            .email("portfolio-" + UUID.randomUUID() + "@example.com")
            .passwordHash("hash")
            .build();
    testUser = userRepository.saveAndFlush(testUser);
  }

  @Test
  void getCurrentUserPortfolioReturnsOnlyAuthenticatedUsersHoldings() throws Exception {
    transactionRepository.saveAndFlush(
        transaction(testUser, "BTC", new BigDecimal("1.5"), new BigDecimal("90000.00000000")));
    transactionRepository.saveAndFlush(
        transaction(testUser, "ETH", new BigDecimal("3.0"), new BigDecimal("2000.00000000")));

    User otherUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("portfolio-other-" + UUID.randomUUID())
                .email("portfolio-other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build());
    transactionRepository.saveAndFlush(
        transaction(otherUser, "SOL", new BigDecimal("5.0"), new BigDecimal("100.00000000")));

    reconcilePortfolio();

    mockMvc
        .perform(get("/api/v1/me/portfolio").with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0].userId").value(testUser.getId().toString()))
        .andExpect(jsonPath("$[*].assetSymbol", Matchers.containsInAnyOrder("BTC", "ETH")))
        .andExpect(jsonPath("$[?(@.assetSymbol=='SOL')]").isEmpty());
  }

  @Test
  void getCurrentUserPortfolioEntryReturnsHoldingBySymbol() throws Exception {
    transactionRepository.saveAndFlush(
        transaction(testUser, "BTC", new BigDecimal("1.5"), new BigDecimal("90000.00000000")));

    reconcilePortfolio();

    mockMvc
        .perform(get("/api/v1/me/portfolio/BTC").with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.assetSymbol").value("BTC"))
        .andExpect(jsonPath("$.totalQuantity").value(1.5))
        .andExpect(jsonPath("$.currentValue").value(135000.00));
  }

  @Test
  void getCurrentUserPortfolioEntryReturns404WhenHoldingDoesNotExist() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/portfolio/BTC").with(authentication(userAuthentication())))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Portfolio Entry Not Found"))
        .andExpect(jsonPath("$.errorCode").value("PORTFOLIO_ENTRY_NOT_FOUND"))
        .andExpect(jsonPath("$.instance").value("/api/v1/me/portfolio/BTC"));
  }

  @Test
  void getCurrentUserPortfolioReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/portfolio"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void getCurrentUserPortfolioReturnsPersistedEntriesWithoutImplicitReconciliation()
      throws Exception {
    Transaction solTransaction =
        transaction(testUser, "SOL", new BigDecimal("7.0"), new BigDecimal("89.35714286"));
    transactionRepository.saveAndFlush(solTransaction);

    portfolioEntryRepository.saveAndFlush(
        PortfolioEntry.builder()
            .userId(testUser.getId())
            .assetSymbol("SOL")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("7.0"))
            .totalInvested(new BigDecimal("625.50"))
            .averagePricePerUnit(new BigDecimal("89.35714286"))
            .lastTransactionPrice(new BigDecimal("89.35714286"))
            .currentValue(new BigDecimal("625.50"))
            .totalProfitLoss(BigDecimal.ZERO)
            .updatedAt(LocalDateTime.now())
            .build());

    transactionRepository.deleteById(solTransaction.getTransactionId());
    transactionRepository.flush();

    mockMvc
        .perform(get("/api/v1/me/portfolio").with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].assetSymbol").value("SOL"));

    org.assertj.core.api.Assertions.assertThat(
            portfolioEntryRepository.findByUserIdAndAssetSymbol(testUser.getId(), "SOL"))
        .isPresent();
  }

  @Test
  void getCurrentUserPortfolioEntryReturnsPersistedHoldingWithoutImplicitCleanup()
      throws Exception {
    portfolioEntryRepository.saveAndFlush(
        PortfolioEntry.builder()
            .userId(testUser.getId())
            .assetSymbol("SOL")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("7.0"))
            .totalInvested(new BigDecimal("625.50"))
            .averagePricePerUnit(new BigDecimal("89.35714286"))
            .lastTransactionPrice(new BigDecimal("89.35714286"))
            .currentValue(new BigDecimal("625.50"))
            .totalProfitLoss(BigDecimal.ZERO)
            .updatedAt(LocalDateTime.now())
            .build());

    mockMvc
        .perform(get("/api/v1/me/portfolio/SOL").with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.assetSymbol").value("SOL"))
        .andExpect(jsonPath("$.totalQuantity").value(7.0));

    org.assertj.core.api.Assertions.assertThat(
            portfolioEntryRepository.findByUserIdAndAssetSymbol(testUser.getId(), "SOL"))
        .isPresent();
  }

  @Test
  void reconcileEndpointRemovesStalePortfolioEntriesAfterManualDatabaseChanges() throws Exception {
    portfolioEntryRepository.saveAndFlush(
        PortfolioEntry.builder()
            .userId(testUser.getId())
            .assetSymbol("SOL")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("7.0"))
            .totalInvested(new BigDecimal("625.50"))
            .averagePricePerUnit(new BigDecimal("89.35714286"))
            .lastTransactionPrice(new BigDecimal("89.35714286"))
            .currentValue(new BigDecimal("625.50"))
            .totalProfitLoss(BigDecimal.ZERO)
            .updatedAt(LocalDateTime.now())
            .build());

    mockMvc
        .perform(post("/api/v1/me/portfolio/reconcile").with(authentication(userAuthentication())))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(
            portfolioEntryRepository.findByUserIdAndAssetSymbol(testUser.getId(), "SOL"))
        .isEmpty();
  }

  @Test
  void reconcileEndpointReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(post("/api/v1/me/portfolio/reconcile"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void getCurrentUserHoldingsPerformanceReturnsAverageCostMetricsForHype() throws Exception {
    org.mockito.Mockito.when(assetPricePort.getCryptoPriceAmount("HYPE"))
        .thenReturn(Mono.just(new BigDecimal("60.54244444")));

    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "HYPE",
            "BUY",
            new BigDecimal("100"),
            new BigDecimal("10.00"),
            new BigDecimal("1000.00"),
            OffsetDateTime.of(2025, 3, 3, 10, 0, 0, 0, ZoneOffset.UTC)));
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "HYPE",
            "BUY",
            new BigDecimal("50"),
            new BigDecimal("10.00"),
            new BigDecimal("500.00"),
            OffsetDateTime.of(2025, 3, 10, 10, 0, 0, 0, ZoneOffset.UTC)));
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "HYPE",
            "BUY",
            new BigDecimal("30"),
            new BigDecimal("23.04600000"),
            new BigDecimal("691.38"),
            OffsetDateTime.of(2025, 3, 17, 10, 0, 0, 0, ZoneOffset.UTC)));
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "HYPE",
            "SELL",
            new BigDecimal("40"),
            new BigDecimal("20.00"),
            new BigDecimal("800.00"),
            OffsetDateTime.of(2025, 3, 24, 10, 0, 0, 0, ZoneOffset.UTC)));
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "HYPE",
            "SELL",
            new BigDecimal("50"),
            new BigDecimal("35.00"),
            new BigDecimal("1750.00"),
            OffsetDateTime.of(2025, 3, 31, 10, 0, 0, 0, ZoneOffset.UTC)));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/HYPE/holdings-performance")
                .param("period", "ALL")
                .with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.isProfit").value(true))
        .andExpect(jsonPath("$.costBasis").value(2191.38))
        .andExpect(jsonPath("$.allTimeProfit").value(5807.44))
        .andExpect(jsonPath("$.allTimeProfitPercent").value(265.01))
        .andExpect(jsonPath("$.firstTransactionDate").value("2025-03-03"))
        .andExpect(jsonPath("$.series", Matchers.hasSize(Matchers.greaterThanOrEqualTo(6))));
  }

  @Test
  void chartMarkersAndRealizedPnlReturnOnlyAuthenticatedUsersTransactions() throws Exception {
    OffsetDateTime transactionDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
    Transaction buy =
        transaction(
            testUser,
            "SOL",
            "BUY",
            new BigDecimal("2.0"),
            new BigDecimal("100.00"),
            new BigDecimal("200.00"),
            transactionDate);
    Transaction sell =
        transaction(
            testUser,
            "SOL",
            "SELL",
            new BigDecimal("1.0"),
            new BigDecimal("125.00"),
            new BigDecimal("125.00"),
            transactionDate.plusHours(2));
    sell.setRealizedPnl(new BigDecimal("24.50"));
    transactionRepository.saveAndFlush(buy);
    transactionRepository.saveAndFlush(sell);

    User otherUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("chart-other-" + UUID.randomUUID())
                .email("chart-other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build());
    Transaction otherSell =
        transaction(
            otherUser,
            "BTC",
            "SELL",
            new BigDecimal("1.0"),
            new BigDecimal("90000.00"),
            new BigDecimal("90000.00"),
            transactionDate);
    otherSell.setRealizedPnl(new BigDecimal("1000.00"));
    transactionRepository.saveAndFlush(otherSell);

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/equity/markers")
                .param("range", "30")
                .with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0].position").value("belowBar"))
        .andExpect(jsonPath("$[0].color").value("#22c55e"))
        .andExpect(jsonPath("$[0].shape").value("arrowUp"))
        .andExpect(jsonPath("$[0].text").value("BUY 2 SOL"))
        .andExpect(jsonPath("$[1].position").value("aboveBar"))
        .andExpect(jsonPath("$[1].color").value("#ef4444"))
        .andExpect(jsonPath("$[1].shape").value("arrowDown"))
        .andExpect(jsonPath("$[1].text").value("SELL 1 SOL"));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/realized")
                .param("range", "30")
                .with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].value").value(24.50));
  }

  @Test
  void chartHistoryRejectsInvalidRange() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "999")
                .with(authentication(userAuthentication())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PORTFOLIO_INVALID_REQUEST"));
  }

  @Test
  void getAssetHoldingsHistoryReturnsUnixSecondSeries() throws Exception {
    OffsetDateTime buyDate = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime sellDate = OffsetDateTime.of(2026, 1, 3, 0, 0, 0, 0, ZoneOffset.UTC);
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "SOL",
            "BUY",
            new BigDecimal("2"),
            new BigDecimal("10.00"),
            new BigDecimal("20.00"),
            buyDate));
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "SOL",
            "SELL",
            new BigDecimal("1"),
            new BigDecimal("12.00"),
            new BigDecimal("12.00"),
            sellDate));

    org.mockito.Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("SOL"),
                org.mockito.ArgumentMatchers.eq("180d")))
        .thenReturn(
            java.util.List.of(
                new PricePoint(
                    java.time.Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("10.50")),
                new PricePoint(
                    java.time.Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("11.00")),
                new PricePoint(
                    java.time.Instant.parse("2026-01-03T00:00:00Z"), new BigDecimal("12.00"))));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/assets/{symbol}/history", "SOL")
                .param("range", "180d")
                .with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(3)))
        .andExpect(jsonPath("$[0].time").value(1767225600L))
        .andExpect(jsonPath("$[0].value").value(21.00))
        .andExpect(jsonPath("$[2].value").value(12.00));
  }

  @Test
  void getAssetHoldingsHistoryAllUsesDynamicResolutionInsteadOfLiteralAllRange() throws Exception {
    OffsetDateTime buyDate = OffsetDateTime.of(2026, 5, 17, 0, 0, 0, 0, ZoneOffset.UTC);
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "HYPE",
            "BUY",
            new BigDecimal("1"),
            new BigDecimal("20.00"),
            new BigDecimal("20.00"),
            buyDate));

    org.mockito.Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("HYPE"),
                org.mockito.ArgumentMatchers.any(
                    com.mx.cryptomonitor.portfolio.domain.model.ChartResolution.class)))
        .thenReturn(
            java.util.List.of(
                new PricePoint(
                    java.time.Instant.parse("2026-05-17T00:00:00Z"), new BigDecimal("20.00")),
                new PricePoint(
                    java.time.Instant.parse("2026-05-18T00:00:00Z"), new BigDecimal("21.00"))));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/assets/{symbol}/history", "HYPE")
                .param("range", "ALL")
                .with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0].value").value(20.00));

    org.mockito.Mockito.verify(marketPriceHistoryPort)
        .getPriceHistory(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("HYPE"),
            org.mockito.ArgumentMatchers.any(
                com.mx.cryptomonitor.portfolio.domain.model.ChartResolution.class));
    org.mockito.Mockito.verify(marketPriceHistoryPort, org.mockito.Mockito.never())
        .getPriceHistory(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("HYPE"),
            org.mockito.ArgumentMatchers.eq("all"));
  }

  @Test
  void getPortfolioTotalHistoryAggregatesAssetsAndKeepsUsersIsolated() throws Exception {
    OffsetDateTime buyDate = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "SOL",
            "BUY",
            new BigDecimal("2"),
            new BigDecimal("10.00"),
            new BigDecimal("20.00"),
            buyDate));
    transactionRepository.saveAndFlush(
        transaction(
            testUser,
            "ETH",
            "BUY",
            new BigDecimal("1"),
            new BigDecimal("100.00"),
            new BigDecimal("100.00"),
            buyDate));

    User otherUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("portfolio-total-other-" + UUID.randomUUID())
                .email("portfolio-total-other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build());
    transactionRepository.saveAndFlush(
        transaction(
            otherUser,
            "SOL",
            "BUY",
            new BigDecimal("10"),
            new BigDecimal("10.00"),
            new BigDecimal("100.00"),
            buyDate));

    org.mockito.Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("SOL"),
                org.mockito.ArgumentMatchers.any(
                    com.mx.cryptomonitor.portfolio.domain.model.ChartResolution.class)))
        .thenAnswer(
            inv -> {
              com.mx.cryptomonitor.portfolio.domain.model.ChartResolution cr = inv.getArgument(2);
              return java.util.List.of(
                  new PricePoint(cr.start(), new BigDecimal("10.00")),
                  new PricePoint(cr.start().plusSeconds(2 * 86400L), new BigDecimal("12.00")));
            });
    org.mockito.Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("ETH"),
                org.mockito.ArgumentMatchers.any(
                    com.mx.cryptomonitor.portfolio.domain.model.ChartResolution.class)))
        .thenAnswer(
            inv -> {
              com.mx.cryptomonitor.portfolio.domain.model.ChartResolution cr = inv.getArgument(2);
              return java.util.List.of(
                  new PricePoint(cr.start().plusSeconds(86400L), new BigDecimal("100.00")));
            });

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "30d")
                .param("assetTypes", "CRYPTO")
                .with(authentication(userAuthentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.range").value("30d"))
        .andExpect(jsonPath("$.meta.resolution").value("INTRADAY"))
        .andExpect(jsonPath("$.meta.currency").value("USD"))
        .andExpect(jsonPath("$.meta.points").value(3))
        .andExpect(jsonPath("$.series", Matchers.hasSize(3)))
        .andExpect(jsonPath("$.series[0].value").value(20.00))
        .andExpect(jsonPath("$.series[1].value").value(120.00))
        .andExpect(jsonPath("$.series[2].value").value(124.00));
  }

  @Test
  void getPortfolioTotalHistoryRejectsPrincipalWithoutUserRole() throws Exception {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(testUser.getEmail(), null, "ROLE_ADMIN");
    authentication.setAuthenticated(true);
    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "30d")
                .with(authentication(authentication)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  private Transaction transaction(
      User user,
      String assetSymbol,
      String transactionType,
      BigDecimal quantity,
      BigDecimal pricePerUnit,
      BigDecimal totalValue,
      OffsetDateTime transactionDate) {
    return Transaction.builder()
        .user(user)
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol(assetSymbol)
        .assetType(AssetType.CRYPTO)
        .transactionType(transactionType)
        .quantity(quantity)
        .pricePerUnit(pricePerUnit)
        .totalValue(totalValue)
        .transactionDate(transactionDate)
        .fee(BigDecimal.ZERO)
        .createdAt(transactionDate)
        .updatedAt(transactionDate)
        .build();
  }

  private Transaction transaction(
      User user, String assetSymbol, BigDecimal quantity, BigDecimal pricePerUnit) {
    BigDecimal totalValue = quantity.multiply(pricePerUnit).setScale(2, RoundingMode.HALF_UP);
    return transaction(
        user,
        assetSymbol,
        "BUY",
        quantity,
        pricePerUnit,
        totalValue,
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  private TestingAuthenticationToken userAuthentication() {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(testUser.getEmail(), null, "ROLE_USER");
    authentication.setAuthenticated(true);
    return authentication;
  }

  private void reconcilePortfolio() throws Exception {
    mockMvc
        .perform(post("/api/v1/me/portfolio/reconcile").with(authentication(userAuthentication())))
        .andExpect(status().isNoContent());
  }
}
