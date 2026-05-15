package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolutionStrategy;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoinGeckoFallbackTest {

  @Mock PriceHistoryCachePort cachePort;
  @Mock BinanceMarketPriceHistoryAdapter binanceAdapter;
  @Mock CoinGeckoMarketPriceHistoryAdapter coinGeckoAdapter;

  CachedMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() {
    when(binanceAdapter.supports(AssetType.CRYPTO)).thenReturn(true);
    when(coinGeckoAdapter.supports(AssetType.CRYPTO)).thenReturn(true);
    // providers ordered: Binance first, CoinGecko second
    adapter = new CachedMarketPriceHistoryAdapter(cachePort, List.of(binanceAdapter, coinGeckoAdapter));
  }

  @Test
  void whenBinanceThrowsUnknownSymbol_coinGeckoIsUsedAsFallback() {
    List<PricePoint> prices = List.of(
        new PricePoint(Instant.now(), new BigDecimal("2000")));

    when(cachePort.getPriceHistory(any(), any(), any())).thenReturn(List.of());
    when(cachePort.acquireLoadLock(any(), any(), any(), any())).thenReturn(true);
    when(binanceAdapter.fetchPriceHistory(any(AssetType.class), any(), any(ChartResolution.class)))
        .thenThrow(new UnknownAssetSymbolException("SHIBUSDT not found on Binance"));
    when(coinGeckoAdapter.fetchPriceHistory(any(AssetType.class), any(), any(ChartResolution.class)))
        .thenReturn(prices);

    ChartResolution cr = new ChartResolutionStrategy().resolve(
        Instant.now().minus(Duration.ofDays(30)),
        Instant.now());

    List<PricePoint> result = adapter.getPriceHistory(AssetType.CRYPTO, "SHIB", cr);

    assertThat(result).isEqualTo(prices);
    verify(binanceAdapter).fetchPriceHistory(any(AssetType.class), any(), any(ChartResolution.class));
    verify(coinGeckoAdapter).fetchPriceHistory(any(AssetType.class), any(), any(ChartResolution.class));
  }

  @Test
  void whenBinanceSucceeds_coinGeckoIsNotCalled() {
    List<PricePoint> prices = List.of(
        new PricePoint(Instant.now(), new BigDecimal("45000")));

    when(cachePort.getPriceHistory(any(), any(), any())).thenReturn(List.of());
    when(cachePort.acquireLoadLock(any(), any(), any(), any())).thenReturn(true);
    when(binanceAdapter.fetchPriceHistory(any(AssetType.class), any(), any(ChartResolution.class)))
        .thenReturn(prices);

    ChartResolution cr = new ChartResolutionStrategy().resolve(
        Instant.now().minus(Duration.ofDays(30)),
        Instant.now());

    List<PricePoint> result = adapter.getPriceHistory(AssetType.CRYPTO, "BTC", cr);

    assertThat(result).isEqualTo(prices);
    verify(coinGeckoAdapter, never()).fetchPriceHistory(any(AssetType.class), any(), any(ChartResolution.class));
  }

  @Test
  void whenBothProvidersUnknownSymbol_throwsException() {
    when(cachePort.getPriceHistory(any(), any(), any())).thenReturn(List.of());
    when(cachePort.acquireLoadLock(any(), any(), any(), any())).thenReturn(true);
    when(binanceAdapter.fetchPriceHistory(any(AssetType.class), any(), any(ChartResolution.class)))
        .thenThrow(new UnknownAssetSymbolException("Not on Binance"));
    when(coinGeckoAdapter.fetchPriceHistory(any(AssetType.class), any(), any(ChartResolution.class)))
        .thenThrow(new UnknownAssetSymbolException("Not on CoinGecko"));

    ChartResolution cr = new ChartResolutionStrategy().resolve(
        Instant.now().minus(Duration.ofDays(30)),
        Instant.now());

    assertThatThrownBy(() -> adapter.getPriceHistory(AssetType.CRYPTO, "UNKNOWN", cr))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }
}
