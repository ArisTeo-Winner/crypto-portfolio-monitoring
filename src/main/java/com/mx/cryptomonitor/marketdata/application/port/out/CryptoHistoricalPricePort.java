package com.mx.cryptomonitor.marketdata.application.port.out;

import java.time.Instant;

import reactor.core.publisher.Mono;

/**
 * Public market-data contract for retrieving historical crypto price series.
 *
 * <p>The caller provides the canonical provider asset identifier expected by the historical
 * provider implementation. For CoinGecko-backed adapters this identifier should be the CoinGecko
 * coin id, for example {@code bitcoin} or {@code ethereum}.
 */
public interface CryptoHistoricalPricePort {

  /**
   * Returns a historical USD price series for the requested crypto asset in the inclusive time
   * range.
   */
  Mono<CryptoHistoricalPriceSeries> getHistoricalUsdPrices(
      String assetId, Instant fromInclusive, Instant toInclusive);

  /**
   * Returns a historical USD price series for the requested crypto asset using the provider's
   * fixed-day history endpoint, for example CoinGecko {@code /market_chart?days=30}.
   */
  Mono<CryptoHistoricalPriceSeries> getHistoricalUsdPrices(String assetId, int days);
}
