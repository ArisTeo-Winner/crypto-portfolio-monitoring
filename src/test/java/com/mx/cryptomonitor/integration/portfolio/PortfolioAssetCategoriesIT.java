package com.mx.cryptomonitor.integration.portfolio;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class PortfolioAssetCategoriesIT {

  private static final String URL = "/api/v1/me/portfolio/asset-categories";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;
  @MockBean private AssetPricePort assetPricePort;

  private User testUser;

  @BeforeEach
  void setup() {
    Mockito.when(assetPricePort.getCryptoPriceAmount(Mockito.anyString())).thenReturn(Mono.empty());
    transactionRepository.deleteAll();
    userRepository.deleteAll();
    testUser =
        userRepository.saveAndFlush(
            User.builder()
                .username("categories-" + UUID.randomUUID())
                .email("cat-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build());
  }

  @Test
  void emptyPortfolio_returnsEmptyList() throws Exception {
    mockMvc
        .perform(get(URL).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void onlyCryptoTransactions_returnsOnlyCryptoCategory() throws Exception {
    save("BTC", com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO);
    save("ETH", com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO);

    mockMvc
        .perform(get(URL).with(auth()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].key", is("CRYPTO")))
        .andExpect(jsonPath("$[0].label", is("Crypto")))
        .andExpect(jsonPath("$[0].icon", is("crypto")))
        .andExpect(jsonPath("$[0].assetCount", is(2)))
        .andExpect(jsonPath("$[0].symbols", containsInAnyOrder("BTC", "ETH")));
  }

  @Test
  void mixedTransactions_returnsCategoriesInCanonicalOrder() throws Exception {
    // Registrar todos los tipos en orden inverso al canónico para verificar que el servicio
    // impone el orden del enum, no el orden de inserción
    save("BONOS-MX", com.mx.cryptomonitor.transaction.domain.model.AssetType.BOND);
    save("EURUSD", com.mx.cryptomonitor.transaction.domain.model.AssetType.FOREX);
    save("CL1", com.mx.cryptomonitor.transaction.domain.model.AssetType.FUTURES);
    save("SP500", com.mx.cryptomonitor.transaction.domain.model.AssetType.INDEX);
    save("SPY", com.mx.cryptomonitor.transaction.domain.model.AssetType.ETF);
    save("AAPL", com.mx.cryptomonitor.transaction.domain.model.AssetType.STOCK);
    save("BTC", com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO);

    mockMvc
        .perform(get(URL).with(auth()))
        .andDo(print())
        .andExpect(status().isOk())
        // 7 categorías en orden canónico del enum: CRYPTO, STOCK, ETF, INDEX, FUTURES, FOREX, BONDS
        .andExpect(jsonPath("$", hasSize(7)))
        .andExpect(jsonPath("$[0].key", is("CRYPTO")))
        .andExpect(jsonPath("$[1].key", is("STOCK")))
        .andExpect(jsonPath("$[2].key", is("ETF")))
        .andExpect(jsonPath("$[3].key", is("INDEX")))
        .andExpect(jsonPath("$[4].key", is("FUTURES")))
        .andExpect(jsonPath("$[5].key", is("FOREX")))
        .andExpect(jsonPath("$[6].key", is("BONDS")));
  }

  @Test
  void indiceTransaction_appearsAsIndexCategory() throws Exception {
    save("SP500", com.mx.cryptomonitor.transaction.domain.model.AssetType.INDEX);

    mockMvc
        .perform(get(URL).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].key", is("INDEX")))
        .andExpect(jsonPath("$[0].label", is("Indices")))
        .andExpect(jsonPath("$[0].symbols[0]", is("SP500")));
  }

  @Test
  void bonosTransaction_appearsAsBondsCategory() throws Exception {
    save("BTC", com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO);
    save("BONOS-MX", com.mx.cryptomonitor.transaction.domain.model.AssetType.BOND);

    mockMvc
        .perform(get(URL).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].key", is("CRYPTO")))
        .andExpect(jsonPath("$[1].key", is("BONDS")))
        .andExpect(jsonPath("$[1].label", is("Bonds")))
        .andExpect(jsonPath("$[1].symbols[0]", is("BONOS-MX")));
  }

  @Test
  void futuresTransaction_appearAsFuturesCategory() throws Exception {
    save("CL1", com.mx.cryptomonitor.transaction.domain.model.AssetType.FUTURES);

    mockMvc
        .perform(get(URL).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].key", is("FUTURES")))
        .andExpect(jsonPath("$[0].label", is("Futures")))
        .andExpect(jsonPath("$[0].symbols[0]", is("CL1")));
  }

  @Test
  void forexTransaction_appearsAsForexCategory() throws Exception {
    save("EURUSD", com.mx.cryptomonitor.transaction.domain.model.AssetType.FOREX);

    mockMvc
        .perform(get(URL).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].key", is("FOREX")))
        .andExpect(jsonPath("$[0].label", is("Forex")))
        .andExpect(jsonPath("$[0].symbols[0]", is("EURUSD")));
  }

  @Test
  void duplicateSymbolsAcrossTransactions_deduplicatesInCategory() throws Exception {
    // El usuario compró BTC en varias transacciones → debe aparecer una sola vez
    save("BTC", com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO);
    save("BTC", com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO);
    save("ETH", com.mx.cryptomonitor.transaction.domain.model.AssetType.CRYPTO);

    mockMvc
        .perform(get(URL).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].assetCount", is(2))) // BTC + ETH, no 3
        .andExpect(jsonPath("$[0].symbols", hasSize(2)))
        .andExpect(jsonPath("$[0].symbols", containsInAnyOrder("BTC", "ETH")));
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void save(String symbol, com.mx.cryptomonitor.transaction.domain.model.AssetType type) {
    OffsetDateTime now = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    transactionRepository.saveAndFlush(
        Transaction.builder()
            .user(testUser)
            .portfolioEntryId(UUID.randomUUID())
            .assetSymbol(symbol)
            .assetType(type)
            .transactionType("BUY")
            .quantity(BigDecimal.ONE)
            .pricePerUnit(new BigDecimal("100"))
            .totalValue(new BigDecimal("100"))
            .transactionDate(now)
            .fee(BigDecimal.ZERO)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
    TestingAuthenticationToken token =
        new TestingAuthenticationToken(testUser.getEmail(), null, "ROLE_USER");
    token.setAuthenticated(true);
    return authentication(token);
  }
}
