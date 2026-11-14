package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.dto.response.DividendGrowthResponse;
import com.mx.cryptomonitor.marketdata.application.service.DividendGrowthService;
import com.mx.cryptomonitor.marketdata.application.service.HybridQuoteService;
import com.mx.cryptomonitor.marketdata.domain.model.AssetDividendStatusEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.DividendActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.DividendEligibilityStatus;
import com.mx.cryptomonitor.marketdata.domain.model.HybridPriceQuote;
import com.mx.cryptomonitor.marketdata.domain.repository.AssetDividendStatusRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.DividendActionDetailRepository;

@ExtendWith(MockitoExtension.class)
class DividendGrowthServiceTest {

  @Mock private DividendActionDetailRepository dividendDetailRepository;
  @Mock private AssetDividendStatusRepository dividendStatusRepository;
  @Mock private CorporateActionRepository corporateActionRepository;
  @Mock private HybridQuoteService hybridQuoteService;

  /** Fija el anio en curso para una CAGR determinista. */
  private DividendGrowthService serviceAtYear(int year) {
    return new DividendGrowthService(
        dividendDetailRepository,
        dividendStatusRepository,
        corporateActionRepository,
        hybridQuoteService) {
      @Override
      protected int currentYear() {
        return year;
      }
    };
  }

  private HybridPriceQuote quote(String symbol, String price, String currency) {
    return new HybridPriceQuote(
        symbol, new BigDecimal(price), currency, "orchestrator", OffsetDateTime.now());
  }

  private Object[] row(int year, String dps) {
    return new Object[] {LocalDate.of(year, 6, 10), new BigDecimal(dps), "USD"};
  }

  @Test
  void mapsRecurringDpsAndComputesCagr() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("KO"))
        .thenReturn(List.of(row(2022, "2.00"), row(2023, "2.20"), row(2024, "0.60")));

    DividendGrowthResponse response = serviceAtYear(2024).growthFor("ko");

    assertThat(response.symbol()).isEqualTo("KO");
    assertThat(response.annualDividends())
        .extracting(DividendGrowthResponse.AnnualDividendPoint::year)
        .containsExactly(2022, 2023); // 2024 excluido
    assertThat(response.cagr1y()).isEqualByComparingTo("0.100000");
    assertThat(response.cagr3y()).isNull();
    assertThat(response.currency()).isEqualTo("USD");
  }

  @Test
  void blankSymbolYieldsEmptyResponse() {
    DividendGrowthResponse response = serviceAtYear(2024).growthFor("  ");

    assertThat(response.annualDividends()).isEmpty();
    assertThat(response.cagr1y()).isNull();
  }

  @Test
  void unknownSymbolWithNoDividendsYieldsEmpty() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("NODIV")).thenReturn(List.of());

    DividendGrowthResponse response = serviceAtYear(2024).growthFor("NODIV");

    assertThat(response.symbol()).isEqualTo("NODIV");
    assertThat(response.annualDividends()).isEmpty();
    // ADR-0025 F4: sin fila de estado => UNKNOWN ("verificando"), no se confunde con "no paga".
    assertThat(response.eligibilityStatus()).isEqualTo("UNKNOWN");
    assertThat(response.lastCheckedAt()).isNull();
  }

  @Test
  void exposesEligibilityStatusAndLastCheckedFromCatalog() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("GOOGL"))
        .thenReturn(List.<Object[]>of(row(2024, "0.20")));
    OffsetDateTime checkedAt = OffsetDateTime.parse("2026-11-06T00:00:00Z");
    when(dividendStatusRepository.findByAssetSymbol("GOOGL"))
        .thenReturn(
            Optional.of(
                AssetDividendStatusEntity.builder()
                    .assetSymbol("GOOGL")
                    .status(DividendEligibilityStatus.PAYS)
                    .lastCheckedAt(checkedAt)
                    .build()));

    DividendGrowthResponse response = serviceAtYear(2025).growthFor("googl");

    assertThat(response.eligibilityStatus()).isEqualTo("PAYS");
    assertThat(response.lastCheckedAt()).isEqualTo(checkedAt);
  }

  /**
   * ADR-0026: el proximo dividendo anunciado se expone con su ex-date, pago, monto y frecuencia.
   */
  @Test
  void exposesNextAnnouncedDividend() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("GOOGL")).thenReturn(List.of());
    UUID caId = UUID.randomUUID();
    CorporateActionEntity next =
        CorporateActionEntity.builder()
            .id(caId)
            .pivotalDate(LocalDate.of(2026, 12, 7))
            .paymentDate(LocalDate.of(2026, 12, 14))
            .build();
    when(corporateActionRepository
            .findFirstByAssetSymbolAndActionTypeAndPaymentDateGreaterThanEqualOrderByPaymentDateAsc(
                eq("GOOGL"), eq(CorporateActionType.DIVIDEND), any()))
        .thenReturn(Optional.of(next));
    when(dividendDetailRepository.findById(caId))
        .thenReturn(
            Optional.of(
                DividendActionDetailEntity.builder()
                    .amountPerShare(new BigDecimal("0.22"))
                    .currency("USD")
                    .frequency((short) 4)
                    .build()));

    DividendGrowthResponse response = serviceAtYear(2026).growthFor("googl");

    assertThat(response.nextDividend()).isNotNull();
    assertThat(response.nextDividend().exDividendDate()).isEqualTo(LocalDate.of(2026, 12, 7));
    assertThat(response.nextDividend().paymentDate()).isEqualTo(LocalDate.of(2026, 12, 14));
    assertThat(response.nextDividend().amountPerShare()).isEqualByComparingTo("0.22");
    assertThat(response.nextDividend().currency()).isEqualTo("USD");
    assertThat(response.nextDividend().frequency()).isEqualTo((short) 4);
  }

  /** Sin dividendo futuro anunciado, nextDividend es null. */
  @Test
  void nextDividendIsNullWhenNoneUpcoming() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("NODIV")).thenReturn(List.of());

    DividendGrowthResponse response = serviceAtYear(2026).growthFor("NODIV");

    assertThat(response.nextDividend()).isNull();
  }

  /**
   * ADR-0031: yield en moneda nativa = DPS del último año completo ÷ precio actual pedido en esa
   * misma moneda. 2.20 USD ÷ 60.00 USD = 0.036667. priceBasis refleja el precio usado.
   */
  @Test
  void computesNativeCurrencyYieldFromLastCompleteYear() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("KO"))
        .thenReturn(List.of(row(2022, "2.00"), row(2023, "2.20"), row(2024, "0.60")));
    when(hybridQuoteService.getCurrentPrice(eq("KO"), eq("USD"), isNull()))
        .thenReturn(quote("KO", "60.00", "USD"));

    DividendGrowthResponse response = serviceAtYear(2024).growthFor("ko");

    assertThat(response.yieldPct()).isEqualByComparingTo("0.036667");
    assertThat(response.priceBasis()).isNotNull();
    assertThat(response.priceBasis().price()).isEqualByComparingTo("60.00");
    assertThat(response.priceBasis().currency()).isEqualTo("USD");
  }

  /** Un fallo del proveedor de precio deja el yield nulo sin tumbar el endpoint. */
  @Test
  void yieldIsNullWhenPriceProviderFails() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("KO"))
        .thenReturn(List.of(row(2022, "2.00"), row(2023, "2.20")));
    when(hybridQuoteService.getCurrentPrice(eq("KO"), eq("USD"), isNull()))
        .thenThrow(new RuntimeException("proveedor caído"));

    DividendGrowthResponse response = serviceAtYear(2024).growthFor("ko");

    assertThat(response.yieldPct()).isNull();
    assertThat(response.priceBasis()).isNull();
    assertThat(response.currency()).isEqualTo("USD"); // el resto de la respuesta intacto
  }

  /** Sin historia de dividendos no se intenta calcular yield (no se pide precio). */
  @Test
  void noYieldWithoutDividendHistory() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("NODIV")).thenReturn(List.of());

    DividendGrowthResponse response = serviceAtYear(2024).growthFor("NODIV");

    assertThat(response.yieldPct()).isNull();
    assertThat(response.priceBasis()).isNull();
  }
}
