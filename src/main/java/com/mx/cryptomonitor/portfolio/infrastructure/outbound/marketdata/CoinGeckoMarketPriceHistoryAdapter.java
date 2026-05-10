package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

@Component
@Order(0)
public class CoinGeckoMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private static final String USD = "usd";

  private final WebClient webClient;
  private final boolean enabled;
  private final Duration responseTimeout;
  private final AssetCatalogQueryPort assetCatalogQueryPort;

  public CoinGeckoMarketPriceHistoryAdapter(
      @Qualifier("coinGeckoWebClient") WebClient webClient,
      @Value("${external.providers.coingecko.enabled:true}") boolean enabled,
      @Value("${external.providers.coingecko.response-timeout:PT8S}") Duration responseTimeout,
      AssetCatalogQueryPort assetCatalogQueryPort) {
    this.webClient = webClient;
    this.enabled = enabled;
    this.responseTimeout = responseTimeout;
    this.assetCatalogQueryPort = assetCatalogQueryPort;
  }

  @Override
  public boolean supports(AssetType assetType) {
    return assetType == AssetType.CRYPTO;
  }

  @Override
  public List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, HoldingsHistoryRange range) {
    String assetId =
        assetCatalogQueryPort
            .findAssetIdBySymbol(symbol)
            .orElseThrow(() -> new UnknownAssetSymbolException("Unknown asset symbol: " + symbol));
    return fetchCoinGeckoPrices(assetId, range.days());
  }

  private List<PricePoint> fetchCoinGeckoPrices(String assetId, int days) {
    if (!enabled) {
      throw new MarketDataServerException("CoinGecko historical integration is disabled");
    }

    CoinGeckoMarketChartResponse response =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/coins/{id}/market_chart")
                        .queryParam("vs_currency", USD)
                        .queryParam("days", days)
                        .build(assetId))
            .retrieve()
            .onStatus(
                status -> status.value() == 429,
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("CoinGecko rate limit exceeded")
                        .map(MarketDataRateLimitException::new))
            .onStatus(
                HttpStatusCode::isError,
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("CoinGecko historical price error")
                        .map(MarketDataServerException::new))
            .bodyToMono(CoinGeckoMarketChartResponse.class)
            .timeout(responseTimeout)
            .block();

    if (response == null || response.prices() == null) {
      return List.of();
    }

    return response.prices().stream()
        .filter(raw -> raw != null && raw.size() >= 2)
        .map(raw -> new PricePoint(Instant.ofEpochMilli(raw.get(0).longValue()), price(raw.get(1))))
        .sorted(Comparator.comparing(PricePoint::time))
        .toList();
  }

  private BigDecimal price(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
