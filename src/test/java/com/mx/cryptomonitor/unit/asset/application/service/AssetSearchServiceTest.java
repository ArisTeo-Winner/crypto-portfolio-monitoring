package com.mx.cryptomonitor.unit.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.dto.response.AssetSearchResponse;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.application.service.AssetSearchService;
import com.mx.cryptomonitor.asset.domain.exception.CatalogPlanRestrictedException;

class AssetSearchServiceTest {

  private final CatalogStorePort redisService = mock(CatalogStorePort.class);
  private final CatalogFetchPort fmpAdapter = mock(CatalogFetchPort.class);
  private AssetSearchService service;

  @BeforeEach
  void setUp() {
    service = new AssetSearchService(redisService, fmpAdapter);
    when(redisService.getTopSymbols(anyString(), anyInt())).thenReturn(List.of());
    when(redisService.findEntry(anyString())).thenReturn(Optional.empty());
    when(fmpAdapter.fetchTopStocks(anyInt())).thenReturn(List.of());
  }

  @Test
  void searchReturnsEmptyWhenRedisAndFmpAreEmpty() {
    AssetSearchResponse result = service.search("btc", 10);

    assertThat(result.query()).isEqualTo("btc");
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void searchReturnsRedisResultsWhenPresent() {
    AssetCatalogDto ethEntry =
        new AssetCatalogDto("ETH", "Ethereum", "CRYPTO", null, null, null, null);
    when(redisService.getTopSymbols("catalog:search:crypto", 50)).thenReturn(List.of("ETH"));
    when(redisService.findEntry("ETH")).thenReturn(Optional.of(ethEntry));

    AssetSearchResponse result = service.search("eth", 10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).symbol()).isEqualTo("ETH");
    assertThat(result.items().get(0).name()).isEqualTo("Ethereum");
  }

  @Test
  void searchReturnsEmptyWhenOnDemandLookupThrowsCatalogFetchException() {
    when(fmpAdapter.fetchTopStocks(anyInt()))
        .thenThrow(new CatalogPlanRestrictedException("fetchTopStocks: endpoint restricted"));

    AssetSearchResponse result = service.search("xyz", 10);

    assertThat(result.items()).isEmpty();
    assertThat(result.total()).isZero();
  }

  @Test
  void searchRejectsBlankQuery() {
    assertThatThrownBy(() -> service.search("   ", 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query parameter q is required.");
  }

  @Test
  void searchRejectsLimitBelowRange() {
    assertThatThrownBy(() -> service.search("btc", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query parameter limit must be between 1 and 20.");
  }

  @Test
  void searchRejectsLimitAboveRange() {
    assertThatThrownBy(() -> service.search("btc", 21))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query parameter limit must be between 1 and 20.");
  }

  @Test
  void findAssetIdBySymbolReturnsFromRedis() {
    AssetCatalogDto dto =
        new AssetCatalogDto("AAPL", "Apple Inc.", "STOCK", null, null, "USD", null);
    when(redisService.findEntry("AAPL")).thenReturn(Optional.of(dto));

    Optional<String> result = service.findAssetIdBySymbol("AAPL");

    assertThat(result).contains("AAPL");
  }

  @Test
  void findNameBySymbolReturnsFromRedis() {
    AssetCatalogDto dto =
        new AssetCatalogDto("NVDA", "NVIDIA Corp", "STOCK", null, null, "USD", null);
    when(redisService.findEntry("NVDA")).thenReturn(Optional.of(dto));

    Optional<String> result = service.findNameBySymbol("NVDA");

    assertThat(result).contains("NVIDIA Corp");
  }
}
