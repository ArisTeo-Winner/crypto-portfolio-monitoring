package com.mx.cryptomonitor.unit.tax.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.tax.domain.model.MarketJurisdiction;
import com.mx.cryptomonitor.tax.domain.model.TaxRegime;
import com.mx.cryptomonitor.tax.domain.service.TaxRegimeResolver;

class TaxRegimeResolverTest {

  private final TaxRegimeResolver resolver = new TaxRegimeResolver();

  @Test
  void mxnStockIsMexicanBolsaRegime() {
    assertThat(resolver.resolve("STOCK", "MXN")).isEqualTo(TaxRegime.MX_BOLSA);
    assertThat(resolver.resolve("ETF", "mxn")).isEqualTo(TaxRegime.MX_BOLSA);
  }

  @Test
  void usdStockIsUsEquityRegime() {
    assertThat(resolver.resolve("STOCK", "USD")).isEqualTo(TaxRegime.US_EQUITY);
  }

  @Test
  void cryptoBondsAndUnknownsGetTheirOwnRegime() {
    assertThat(resolver.resolve("CRYPTO", "USD")).isEqualTo(TaxRegime.CRYPTO);
    assertThat(resolver.resolve("GOVERNMENT_BOND", "MXN")).isEqualTo(TaxRegime.INTEREST);
    assertThat(resolver.resolve("CORPORATE_BOND", "MXN")).isEqualTo(TaxRegime.INTEREST);
    assertThat(resolver.resolve("FOREX", "USD")).isEqualTo(TaxRegime.UNSUPPORTED);
  }

  @Test
  void equityWithoutResolvableCurrencyIsUnsupportedForNow() {
    assertThat(resolver.resolve("STOCK", null)).isEqualTo(TaxRegime.UNSUPPORTED);
    assertThat(resolver.resolve("STOCK", "EUR")).isEqualTo(TaxRegime.UNSUPPORTED);
  }

  @Test
  void jurisdictionIsInferredFromCurrency() {
    assertThat(resolver.jurisdictionOf("MXN")).isEqualTo(MarketJurisdiction.MX);
    assertThat(resolver.jurisdictionOf("USD")).isEqualTo(MarketJurisdiction.US);
    assertThat(resolver.jurisdictionOf(null)).isEqualTo(MarketJurisdiction.OTHER);
  }
}
