package com.mx.cryptomonitor.unit.asset.infrastructure.outbound.companieslogo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.asset.infrastructure.outbound.companieslogo.CompaniesLogoAdapter;

class CompaniesLogoAdapterTest {

  private final CompaniesLogoAdapter adapter = new CompaniesLogoAdapter();

  @Test
  void buildLogoUrlReturnsExpectedUrl() {
    assertThat(adapter.buildLogoUrl("SPY"))
        .isEqualTo("https://companieslogo.com/api/starter/stock-symbol/SPY");
  }

  @Test
  void buildLogoUrlUppercasesTicker() {
    assertThat(adapter.buildLogoUrl("qqq"))
        .isEqualTo("https://companieslogo.com/api/starter/stock-symbol/QQQ");
  }

  @Test
  void buildLogoUrlReturnsNullForNullTicker() {
    assertThat(adapter.buildLogoUrl(null)).isNull();
  }

  @Test
  void buildLogoUrlReturnsNullForBlankTicker() {
    assertThat(adapter.buildLogoUrl("   ")).isNull();
  }
}
