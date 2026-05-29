package com.mx.cryptomonitor.integration.portfolio;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
@ActiveProfiles("test")
class PortfolioAllRangeEndpointIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;
  @MockBean private AssetPricePort assetPricePort;
  @MockBean private CryptoHistoricalPricePort cryptoHistoricalPricePort;
  @MockBean private MarketPriceHistoryPort marketPriceHistoryPort;

  private User testUser;

  @BeforeEach
  void setup() {
    Mockito.when(assetPricePort.getCryptoPriceAmount(Mockito.anyString())).thenReturn(Mono.empty());

    transactionRepository.deleteAll();
    userRepository.deleteAll();

    testUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("all-range-user-" + UUID.randomUUID())
                .email("all-range-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build());
  }

  @Test
  void rangeAllReturnsEnrichedResponseStartingFromFirstTransaction() throws Exception {
    // Primera compra: BTC hace ~1 año (2025-01-15)
    OffsetDateTime firstBuy = OffsetDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
    // Segunda compra: ETH hace ~6 meses (2024-11-01)
    OffsetDateTime secondBuy = OffsetDateTime.of(2024, 11, 1, 9, 0, 0, 0, ZoneOffset.UTC);

    transactionRepository.saveAndFlush(btcBuy(firstBuy, "1", "95000"));
    transactionRepository.saveAndFlush(ethBuy(secondBuy, "3", "3200"));

    // Precios simulados para BTC (range=all → CoinGecko devolvería días desde el max)
    Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                Mockito.eq(AssetType.CRYPTO),
                Mockito.eq("BTC"),
                Mockito.any(ChartResolution.class)))
        .thenReturn(
            List.of(
                priceAt("2024-11-01T00:00:00Z", "68000"),
                priceAt("2025-01-15T00:00:00Z", "95000"),
                priceAt("2025-03-01T00:00:00Z", "82000"),
                priceAt("2025-05-01T00:00:00Z", "97000"),
                priceAt("2025-05-12T00:00:00Z", "103000")));

    // Precios simulados para ETH
    Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                Mockito.eq(AssetType.CRYPTO),
                Mockito.eq("ETH"),
                Mockito.any(ChartResolution.class)))
        .thenReturn(
            List.of(
                priceAt("2024-11-01T00:00:00Z", "3200"),
                priceAt("2025-01-15T00:00:00Z", "3400"),
                priceAt("2025-03-01T00:00:00Z", "2100"),
                priceAt("2025-05-01T00:00:00Z", "1900"),
                priceAt("2025-05-12T00:00:00Z", "2600")));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "ALL")
                .with(authentication(userAuth())))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.range").value("all"))
        .andExpect(jsonPath("$.meta.resolution").value("DAILY"))
        .andExpect(jsonPath("$.meta.currency").value("USD"))
        .andExpect(jsonPath("$.meta.points").isNumber())
        .andExpect(jsonPath("$.series").isArray())
        .andExpect(jsonPath("$.series[0].time").isNumber())
        .andExpect(jsonPath("$.series[0].value").isNumber());
  }

  @Test
  void rangeAllWithSingleAssetReturnsSeriesFromFirstTxDay() throws Exception {
    OffsetDateTime txDate = OffsetDateTime.of(2025, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    transactionRepository.saveAndFlush(btcBuy(txDate, "0.5", "100000"));

    Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                Mockito.eq(AssetType.CRYPTO),
                Mockito.eq("BTC"),
                Mockito.any(ChartResolution.class)))
        .thenReturn(
            List.of(
                priceAt("2025-01-01T00:00:00Z", "90000"), // antes de la primera tx
                priceAt("2025-06-01T00:00:00Z", "100000"),
                priceAt("2025-06-02T00:00:00Z", "101000"),
                priceAt("2025-06-03T00:00:00Z", "99000")));

    mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "ALL")
                .with(authentication(userAuth())))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.range").value("all"))
        .andExpect(jsonPath("$.meta.points").value(3))
        .andExpect(
            jsonPath("$.series[0].time")
                .value(Instant.parse("2025-06-01T00:00:00Z").getEpochSecond()));
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

  private Transaction ethBuy(OffsetDateTime date, String qty, String price) {
    return Transaction.builder()
        .user(testUser)
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol("ETH")
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

  private PricePoint priceAt(String isoTime, String value) {
    return new PricePoint(Instant.parse(isoTime), new BigDecimal(value));
  }

  private TestingAuthenticationToken userAuth() {
    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(testUser.getEmail(), null, "ROLE_USER");
    auth.setAuthenticated(true);
    return auth;
  }
}
