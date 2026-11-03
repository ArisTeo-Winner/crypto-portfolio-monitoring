package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CachedMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.MarketPriceHistoryProvider;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CachedMarketPriceHistoryAdapterObservabilityTest {

  private static final AssetType CRYPTO = AssetType.CRYPTO;
  private static final String SYMBOL = "BTC";
  private static final String RANGE = "30d";
  private static final HoldingsHistoryRange PARSED_RANGE = HoldingsHistoryRange.parse(RANGE);
  private static final String METRIC = "marketdata.pricehistory.fetch";

  private static final List<PricePoint> POINTS =
      List.of(new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("50000.00")));

  private PriceHistoryCachePort cache;
  private SimpleMeterRegistry registry;

  @BeforeEach
  void setUp() {
    cache = mock(PriceHistoryCachePort.class);
    registry = new SimpleMeterRegistry();
    when(cache.getPriceHistory(CRYPTO, SYMBOL, PARSED_RANGE.value())).thenReturn(List.of());
    when(cache.acquireLoadLock(eq(CRYPTO), eq(SYMBOL), eq(PARSED_RANGE.value()), any()))
        .thenReturn(true);
  }

  @Test
  void recordsSuccessOutcomeForTheProviderThatServed() {
    CachedMarketPriceHistoryAdapter adapter =
        new CachedMarketPriceHistoryAdapter(cache, List.of(new SuccessProvider(POINTS)), registry);

    adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    assertThat(counter("SuccessProvider", "success")).isEqualTo(1.0);
  }

  @Test
  void recordsFailureOutcomeOnTheSkippedProviderAndSuccessOnTheFallback() {
    CachedMarketPriceHistoryAdapter adapter =
        new CachedMarketPriceHistoryAdapter(
            cache, List.of(new UnknownSymbolProvider(), new SuccessProvider(POINTS)), registry);

    adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    assertThat(counter("UnknownSymbolProvider", "unknown_symbol")).isEqualTo(1.0);
    assertThat(counter("SuccessProvider", "success")).isEqualTo(1.0);
  }

  private double counter(String provider, String outcome) {
    return registry
        .get(METRIC)
        .tags("provider", provider, "asset_type", "CRYPTO", "outcome", outcome)
        .counter()
        .count();
  }

  private static final class SuccessProvider implements MarketPriceHistoryProvider {
    private final List<PricePoint> points;

    SuccessProvider(List<PricePoint> points) {
      this.points = points;
    }

    @Override
    public boolean supports(AssetType assetType) {
      return assetType == CRYPTO;
    }

    @Override
    public List<PricePoint> fetchPriceHistory(
        AssetType assetType, String symbol, HoldingsHistoryRange range) {
      return points;
    }
  }

  private static final class UnknownSymbolProvider implements MarketPriceHistoryProvider {
    @Override
    public boolean supports(AssetType assetType) {
      return assetType == CRYPTO;
    }

    @Override
    public List<PricePoint> fetchPriceHistory(
        AssetType assetType, String symbol, HoldingsHistoryRange range) {
      throw new UnknownAssetSymbolException("unknown: " + symbol);
    }
  }
}
