package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.UsdMxnRateDetail;
import com.mx.cryptomonitor.portfolio.application.dto.response.MoneyPresentation;
import com.mx.cryptomonitor.portfolio.application.service.MoneyPresentationConverter;
import com.mx.cryptomonitor.portfolio.application.service.MoneyPresentationConverter.PresentationContext;
import com.mx.cryptomonitor.portfolio.domain.model.PresentationCurrency;

@ExtendWith(MockitoExtension.class)
class MoneyPresentationConverterTest {

  private static final OffsetDateTime AS_OF =
      OffsetDateTime.of(2026, 9, 26, 0, 0, 0, 0, ZoneOffset.UTC);

  @Mock private FxRatePort fxRatePort;

  @InjectMocks private MoneyPresentationConverter converter;

  @Test
  void usdTargetPassesAmountsThroughWithRateOneAndNoProvenance() {
    PresentationContext ctx = converter.resolve(PresentationCurrency.USD);

    assertThat(ctx.currency()).isEqualTo(PresentationCurrency.USD);
    assertThat(ctx.rate()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(converter.toDisplay(new BigDecimal("1802.05"), ctx)).isEqualByComparingTo("1802.05");

    MoneyPresentation envelope = converter.toEnvelope(ctx);
    assertThat(envelope.baseCurrency()).isEqualTo("USD");
    assertThat(envelope.displayCurrency()).isEqualTo("USD");
    assertThat(envelope.fxRate()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(envelope.rateProvider()).isNull();
    assertThat(envelope.rateAsOf()).isNull();
  }

  @Test
  void mxnTargetConvertsWithProvenanceAndRoundsToTwoDecimals() {
    when(fxRatePort.usdMxnRateDetail())
        .thenReturn(Optional.of(new UsdMxnRateDetail(new BigDecimal("17.5147"), "BANXICO", AS_OF)));

    PresentationContext ctx = converter.resolve(PresentationCurrency.MXN);

    assertThat(ctx.currency()).isEqualTo(PresentationCurrency.MXN);
    // 1802.05 USD * 17.5147 = 31562.365135 -> HALF_UP -> 31562.37
    assertThat(converter.toDisplay(new BigDecimal("1802.05"), ctx))
        .isEqualByComparingTo("31562.37");

    MoneyPresentation envelope = converter.toEnvelope(ctx);
    assertThat(envelope.displayCurrency()).isEqualTo("MXN");
    assertThat(envelope.fxRate()).isEqualByComparingTo("17.5147");
    assertThat(envelope.rateProvider()).isEqualTo("BANXICO");
    assertThat(envelope.rateAsOf()).isEqualTo(AS_OF);
  }

  @Test
  void mxnFallsBackToUsdWhenNoRateAvailable() {
    when(fxRatePort.usdMxnRateDetail()).thenReturn(Optional.empty());

    PresentationContext ctx = converter.resolve(PresentationCurrency.MXN);

    assertThat(ctx.currency()).isEqualTo(PresentationCurrency.USD);
    assertThat(converter.toDisplay(new BigDecimal("100.00"), ctx)).isEqualByComparingTo("100.00");
    assertThat(converter.toEnvelope(ctx).displayCurrency()).isEqualTo("USD");
  }

  @Test
  void mxnFallsBackToUsdWhenRateIsNonPositive() {
    when(fxRatePort.usdMxnRateDetail())
        .thenReturn(Optional.of(new UsdMxnRateDetail(BigDecimal.ZERO, "BANXICO", AS_OF)));

    PresentationContext ctx = converter.resolve(PresentationCurrency.MXN);

    assertThat(ctx.currency()).isEqualTo(PresentationCurrency.USD);
  }

  @Test
  void nullAmountStaysNull() {
    PresentationContext ctx = converter.resolve(PresentationCurrency.USD);
    assertThat(converter.toDisplay(null, ctx)).isNull();
  }
}
