package com.mx.cryptomonitor.integration.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Verifica que ?assetType=CRYPTO y ?assetType=STOCK devuelven series distintas cuando el usuario
 * tiene transacciones de ambos tipos.
 *
 * <p>Escenario que reproduce el bug reportado en Postman: los tres endpoints (sin filtro,
 * ?assetType=CRYPTO, ?assetType=STOCK) devolvían datos idénticos.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
@ActiveProfiles("test")
class PortfolioAssetTypeFilterIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private AssetPricePort assetPricePort;
  @MockBean private CryptoHistoricalPricePort cryptoHistoricalPricePort;
  @MockBean private MarketPriceHistoryPort marketPriceHistoryPort;

  private User testUser;

  // Precios bien separados para distinguir series en las aserciones
  // BTC: precio bajo (100) → valor CRYPTO bajo
  // AAPL: precio alto (1000) → valor STOCK alto
  private static final BigDecimal BTC_PRICE = new BigDecimal("100");
  private static final BigDecimal BTC_QTY = new BigDecimal("1");
  private static final BigDecimal AAPL_PRICE = new BigDecimal("1000");
  private static final BigDecimal AAPL_QTY = new BigDecimal("1");

  @BeforeEach
  void setup() {
    Mockito.when(assetPricePort.getCryptoPriceAmount(Mockito.anyString())).thenReturn(Mono.empty());

    transactionRepository.deleteAll();
    userRepository.deleteAll();

    testUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("assettype-filter-" + UUID.randomUUID())
                .email("assettype-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build());

    LocalDateTime txDate = LocalDateTime.of(2025, 1, 1, 0, 0, 0);

    // Transacción CRYPTO: 1 BTC a $100
    transactionRepository.saveAndFlush(
        Transaction.builder()
            .user(testUser)
            .portfolioEntryId(UUID.randomUUID())
            .assetSymbol("BTC")
            .assetType(com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO)
            .transactionType("BUY")
            .quantity(BTC_QTY)
            .pricePerUnit(BTC_PRICE)
            .totalValue(BTC_QTY.multiply(BTC_PRICE))
            .transactionDate(txDate)
            .fee(BigDecimal.ZERO)
            .createdAt(txDate)
            .updatedAt(txDate)
            .build());

    // Transacción STOCK: 1 AAPL a $1000
    transactionRepository.saveAndFlush(
        Transaction.builder()
            .user(testUser)
            .portfolioEntryId(UUID.randomUUID())
            .assetSymbol("AAPL")
            .assetType(com.mx.cryptomonitor.transaction.domain.model.AssetType.STOCK)
            .transactionType("BUY")
            .quantity(AAPL_QTY)
            .pricePerUnit(AAPL_PRICE)
            .totalValue(AAPL_QTY.multiply(AAPL_PRICE))
            .transactionDate(txDate)
            .fee(BigDecimal.ZERO)
            .createdAt(txDate)
            .updatedAt(txDate)
            .build());

    // Precios simulados: solo existen para los activos correctos
    Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                Mockito.eq(AssetType.CRYPTO),
                Mockito.eq("BTC"),
                Mockito.any(ChartResolution.class)))
        .thenReturn(
            List.of(
                new PricePoint(Instant.parse("2025-01-01T00:00:00Z"), BTC_PRICE),
                new PricePoint(Instant.parse("2025-01-02T00:00:00Z"), BTC_PRICE)));

    Mockito.when(
            marketPriceHistoryPort.getPriceHistory(
                Mockito.eq(AssetType.STOCK),
                Mockito.eq("AAPL"),
                Mockito.any(ChartResolution.class)))
        .thenReturn(
            List.of(
                new PricePoint(Instant.parse("2025-01-01T00:00:00Z"), AAPL_PRICE),
                new PricePoint(Instant.parse("2025-01-02T00:00:00Z"), AAPL_PRICE)));
  }

  @Test
  void withoutFilter_returnsCombinedPortfolio() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/me/portfolio/history")
                    .param("range", "ALL")
                    .with(authentication(auth())))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode series = parseSeries(result);
    // Total = 1 BTC * $100 + 1 AAPL * $1000 = $1100 en al menos un punto
    BigDecimal maxValue = maxSeriesValue(series);
    assertThat(maxValue)
        .as("sin filtro debe incluir tanto CRYPTO como STOCK → valor máximo ~1100")
        .isGreaterThanOrEqualTo(new BigDecimal("1100"));
  }

  @Test
  void withAssetTypeEqualsCrypto_returnsOnlyCryptoSeries() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/me/portfolio/history")
                    .param("range", "ALL")
                    .param("assetType", "CRYPTO")
                    .with(authentication(auth())))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn();

    JsonNode series = parseSeries(result);
    assertThat(series.isEmpty()).as("?assetType=CRYPTO no debe devolver serie vacía").isFalse();

    BigDecimal maxValue = maxSeriesValue(series);
    // Solo BTC: 1 * 100 = $100. Si llega AAPL ($1000), el filtro falla.
    assertThat(maxValue)
        .as("?assetType=CRYPTO: valor máximo debe ser ~100 (solo BTC), no ~1100 (BTC+AAPL)")
        .isLessThanOrEqualTo(new BigDecimal("200"));
  }

  @Test
  void withAssetTypeEqualsStock_returnsOnlyStockSeries() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/me/portfolio/history")
                    .param("range", "ALL")
                    .param("assetType", "STOCK")
                    .with(authentication(auth())))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn();

    JsonNode series = parseSeries(result);
    assertThat(series.isEmpty()).as("?assetType=STOCK no debe devolver serie vacía").isFalse();

    BigDecimal maxValue = maxSeriesValue(series);
    // Solo AAPL: 1 * 1000 = $1000. Sin BTC ($100) ni combinado ($1100).
    assertThat(maxValue)
        .as("?assetType=STOCK: valor máximo debe ser ~1000 (solo AAPL), no ~1100 (BTC+AAPL)")
        .isBetween(new BigDecimal("800"), new BigDecimal("1200"));
  }

  @Test
  void cryptoAndStockSeriesAreDifferentFromEachOther() throws Exception {
    BigDecimal cryptoMax = maxSeriesValue(parseSeries(doGet("CRYPTO")));
    BigDecimal stockMax = maxSeriesValue(parseSeries(doGet("STOCK")));

    assertThat(cryptoMax)
        .as("CRYPTO y STOCK deben tener valores de serie distintos")
        .isNotEqualByComparingTo(stockMax);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private MvcResult doGet(String assetType) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/me/portfolio/history")
                .param("range", "ALL")
                .param("assetType", assetType)
                .with(authentication(auth())))
        .andExpect(status().isOk())
        .andReturn();
  }

  private JsonNode parseSeries(MvcResult result) throws Exception {
    String body = result.getResponse().getContentAsString();
    JsonNode root = objectMapper.readTree(body);
    // Formato enriquecido: { meta: {...}, series: [...] }
    return root.path("series");
  }

  private BigDecimal maxSeriesValue(JsonNode series) {
    BigDecimal max = BigDecimal.ZERO;
    for (JsonNode point : series) {
      BigDecimal v = point.path("value").decimalValue();
      if (v.compareTo(max) > 0) max = v;
    }
    return max;
  }

  private TestingAuthenticationToken auth() {
    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(testUser.getEmail(), null, "ROLE_USER");
    auth.setAuthenticated(true);
    return auth;
  }
}
