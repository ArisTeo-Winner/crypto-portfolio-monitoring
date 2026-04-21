package com.mx.cryptomonitor.unit.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.asset.application.dto.response.AssetSearchResponse;
import com.mx.cryptomonitor.asset.application.service.AssetSearchService;

class AssetSearchServiceTest {

  private final AssetSearchService service = new AssetSearchService();

  @Test
  void searchShouldTrimQueryAndRespectLimit() {
    AssetSearchResponse result = service.search("  t  ", 2);

    assertThat(result.query()).isEqualTo("t");
    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(item -> item.assetType(), item -> item.supportedForTransactions())
        .containsOnly(org.assertj.core.groups.Tuple.tuple("CRYPTO", true));
  }

  @Test
  void searchShouldPrioritizeExactSymbolMatch() {
    AssetSearchResponse result = service.search("eth", 10);

    assertThat(result.items()).isNotEmpty();
    assertThat(result.items().get(0).symbol()).isEqualTo("ETH");
    assertThat(result.items().get(0).name()).isEqualTo("Ethereum");
  }

  @Test
  void searchShouldReturnEmptyResultWhenNothingMatches() {
    AssetSearchResponse result = service.search("zzz", 10);

    assertThat(result.query()).isEqualTo("zzz");
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void searchShouldRejectBlankQuery() {
    assertThatThrownBy(() -> service.search("   ", 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query parameter q is required.");
  }

  @Test
  void searchShouldRejectLimitBelowRange() {
    assertThatThrownBy(() -> service.search("btc", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query parameter limit must be between 1 and 20.");
  }

  @Test
  void searchShouldRejectLimitAboveRange() {
    assertThatThrownBy(() -> service.search("btc", 21))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query parameter limit must be between 1 and 20.");
  }
}
