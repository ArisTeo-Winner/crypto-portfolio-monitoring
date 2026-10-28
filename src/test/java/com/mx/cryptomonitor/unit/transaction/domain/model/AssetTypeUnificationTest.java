package com.mx.cryptomonitor.unit.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TEST 2a — verifica que los tres módulos tienen exactamente CRYPTO, STOCK, ETF, GOVERNMENT_BOND,
 * CORPORATE_BOND, INDEX, FOREX y que los valores legacy BONOS e INDICE no son parte del enum.
 */
@ExtendWith(MockitoExtension.class)
class AssetTypeUnificationTest {

  private static final String[] EXPECTED =
      new String[] {
        "CRYPTO", "STOCK", "ETF", "GOVERNMENT_BOND", "CORPORATE_BOND", "INDEX", "FOREX"
      };

  // ── transaction.domain.model.AssetType ───────────────────────────────────

  @Test
  void transactionAssetType_hasExactlyExpectedValues() {
    assertThat(com.mx.cryptomonitor.transaction.domain.model.AssetType.values())
        .extracting(Enum::name)
        .containsExactlyInAnyOrder(EXPECTED);
  }

  @Test
  void transactionAssetType_governmentBondIsValid() {
    assertThat(com.mx.cryptomonitor.transaction.domain.model.AssetType.valueOf("GOVERNMENT_BOND"))
        .isEqualTo(com.mx.cryptomonitor.transaction.domain.model.AssetType.GOVERNMENT_BOND);
  }

  @Test
  void transactionAssetType_indexIsValid() {
    assertThat(com.mx.cryptomonitor.transaction.domain.model.AssetType.valueOf("INDEX"))
        .isEqualTo(com.mx.cryptomonitor.transaction.domain.model.AssetType.INDEX);
  }

  @Test
  void transactionAssetType_bonosThrowsIllegalArgumentException() {
    assertThatThrownBy(
            () -> com.mx.cryptomonitor.transaction.domain.model.AssetType.valueOf("BONOS"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void transactionAssetType_indiceThrowsIllegalArgumentException() {
    assertThatThrownBy(
            () -> com.mx.cryptomonitor.transaction.domain.model.AssetType.valueOf("INDICE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── portfolio.domain.model.AssetType ─────────────────────────────────────

  @Test
  void portfolioAssetType_hasExactlyExpectedValues() {
    assertThat(com.mx.cryptomonitor.portfolio.domain.model.AssetType.values())
        .extracting(Enum::name)
        .containsExactlyInAnyOrder(EXPECTED);
  }

  @Test
  void portfolioAssetType_governmentBondIsValid() {
    assertThat(com.mx.cryptomonitor.portfolio.domain.model.AssetType.valueOf("GOVERNMENT_BOND"))
        .isEqualTo(com.mx.cryptomonitor.portfolio.domain.model.AssetType.GOVERNMENT_BOND);
  }

  @Test
  void portfolioAssetType_indexIsValid() {
    assertThat(com.mx.cryptomonitor.portfolio.domain.model.AssetType.valueOf("INDEX"))
        .isEqualTo(com.mx.cryptomonitor.portfolio.domain.model.AssetType.INDEX);
  }

  @Test
  void portfolioAssetType_bonosThrowsIllegalArgumentExceptionViaValueOf() {
    assertThatThrownBy(() -> com.mx.cryptomonitor.portfolio.domain.model.AssetType.valueOf("BONOS"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void portfolioAssetType_indiceThrowsIllegalArgumentExceptionViaValueOf() {
    assertThatThrownBy(
            () -> com.mx.cryptomonitor.portfolio.domain.model.AssetType.valueOf("INDICE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void portfolioAssetType_fromBonosMapsToGovernmentBondForBackwardCompat() {
    assertThat(com.mx.cryptomonitor.portfolio.domain.model.AssetType.from("BONOS"))
        .isEqualTo(com.mx.cryptomonitor.portfolio.domain.model.AssetType.GOVERNMENT_BOND);
  }

  @Test
  void portfolioAssetType_fromIndiceMapsToIndexForBackwardCompat() {
    assertThat(com.mx.cryptomonitor.portfolio.domain.model.AssetType.from("INDICE"))
        .isEqualTo(com.mx.cryptomonitor.portfolio.domain.model.AssetType.INDEX);
  }

  // ── marketdata.domain.model.AssetType ────────────────────────────────────

  @Test
  void marketdataAssetType_hasExactlyExpectedValues() {
    assertThat(com.mx.cryptomonitor.marketdata.domain.model.AssetType.values())
        .extracting(Enum::name)
        .containsExactlyInAnyOrder(EXPECTED);
  }

  @Test
  void marketdataAssetType_governmentBondIsValid() {
    assertThat(com.mx.cryptomonitor.marketdata.domain.model.AssetType.valueOf("GOVERNMENT_BOND"))
        .isEqualTo(com.mx.cryptomonitor.marketdata.domain.model.AssetType.GOVERNMENT_BOND);
  }

  @Test
  void marketdataAssetType_indexIsValid() {
    assertThat(com.mx.cryptomonitor.marketdata.domain.model.AssetType.valueOf("INDEX"))
        .isEqualTo(com.mx.cryptomonitor.marketdata.domain.model.AssetType.INDEX);
  }

  @Test
  void marketdataAssetType_bonosThrowsIllegalArgumentException() {
    assertThatThrownBy(
            () -> com.mx.cryptomonitor.marketdata.domain.model.AssetType.valueOf("BONOS"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void marketdataAssetType_indiceThrowsIllegalArgumentException() {
    assertThatThrownBy(
            () -> com.mx.cryptomonitor.marketdata.domain.model.AssetType.valueOf("INDICE"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
