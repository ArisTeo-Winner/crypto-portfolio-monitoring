package com.mx.cryptomonitor.integration.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

/**
 * Verifica end-to-end (contexto Spring completo) que una posición de renta fija a descuento (CETES,
 * GOVERNMENT_BOND) se valúa por devengo sin proveedor de precios: el endpoint ya no responde 500 y
 * la serie devengada queda entre el costo y el valor nominal. Cubre ADR-0018 B.3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class FixedIncomeAccrualHistoryIT extends InfraIntegrationTest {

  private static final String SYMBOL = "CETES91";
  // 100 títulos comprados a 9.80 (costo 980) con nominal 10.00 (face 1000), plazo de 90 días.
  private static final BigDecimal QUANTITY = new BigDecimal("100");
  private static final BigDecimal PRICE_PER_UNIT = new BigDecimal("9.80");
  private static final BigDecimal COST = new BigDecimal("980.00");
  private static final BigDecimal FACE = new BigDecimal("1000.00");

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;

  private User user;

  @DynamicPropertySource
  static void disableHistoryRateLimit(DynamicPropertyRegistry registry) {
    registry.add("security.portfolio-history-rate-limit.enabled", () -> "false");
  }

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAll();
    userRepository.deleteAll();
    user =
        userRepository.saveAndFlush(
            User.builder()
                .username("cetes-" + UUID.randomUUID())
                .email("cetes-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build());

    // Operación hace 30 días; vence en 60 (plazo 90), de modo que "hoy" cae a 1/3 del devengo.
    OffsetDateTime operation = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
    LocalDate maturity = operation.toLocalDate().plusDays(90);
    transactionRepository.saveAndFlush(
        Transaction.builder()
            .user(user)
            .portfolioEntryId(UUID.randomUUID())
            .assetSymbol(SYMBOL)
            .assetType(AssetType.GOVERNMENT_BOND)
            .transactionType("BUY")
            .quantity(QUANTITY)
            .pricePerUnit(PRICE_PER_UNIT)
            .totalValue(COST)
            .faceValue(FACE)
            .maturityDate(maturity)
            .couponRate(new BigDecimal("11.0000"))
            .currency("MXN")
            .transactionDate(operation)
            .fee(BigDecimal.ZERO)
            .createdAt(operation)
            .updatedAt(operation)
            .build());
  }

  @Test
  void totalHistoryValuesGovernmentBondByAccrualInsteadOfFailing() throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/v1/me/portfolio/history")
                    .param("range", "30d")
                    .param("assetTypes", "GOVERNMENT_BOND")
                    .with(authentication(authToken())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Object> series = JsonPath.read(body, "$.series");
    assertThat(series).isNotEmpty();

    double totalInvested =
        ((Number) JsonPath.read(body, "$.meta.returns.totalInvested")).doubleValue();
    assertThat(totalInvested).isEqualTo(980.0);

    double lastValue =
        ((Number) JsonPath.read(body, "$.series[" + (series.size() - 1) + "].value")).doubleValue();
    // ~1/3 devengado: 980 + (1000-980)/3 ≈ 986.67; estrictamente entre costo y nominal.
    assertThat(lastValue).isBetween(980.01, 999.99);
  }

  @Test
  void assetHistoryEndpointServesGovernmentBondWithoutServerError() throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/v1/me/portfolio/assets/{symbol}/history", SYMBOL)
                    .param("range", "30d")
                    .with(authentication(authToken())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Object> points = JsonPath.read(body, "$");
    assertThat(points).isNotEmpty();

    double lastValue =
        ((Number) JsonPath.read(body, "$[" + (points.size() - 1) + "].value")).doubleValue();
    assertThat(lastValue).isBetween(980.01, 999.99);
  }

  private TestingAuthenticationToken authToken() {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(user.getEmail(), null, "ROLE_USER");
    authentication.setAuthenticated(true);
    return authentication;
  }
}
