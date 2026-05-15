package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

import lombok.extern.slf4j.Slf4j;

@Component
@Order(10)
@Slf4j
public class CoinGeckoMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private static final String USD = "usd";
  private static final Duration MAX_CHUNK = Duration.ofDays(365);

  private final WebClient webClient;
  private final boolean enabled;
  private final Duration responseTimeout;
  private final AssetCatalogQueryPort assetCatalogQueryPort;
  private final Map<String, String> resolvedCoinIds = new ConcurrentHashMap<>();

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
    String assetId = resolveAssetId(symbol);
    int days = range.isAll() ? 365 * 5 : range.days();
    Instant end = Instant.now();
    Instant start = end.minus(Duration.ofDays(days));
    return fetchRangeOrChunked(assetId, start, end);
  }

  @Override
  public List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, ChartResolution chartResolution) {
    String assetId = resolveAssetId(symbol);
    return fetchRangeOrChunked(assetId, chartResolution.start(), chartResolution.end());
  }

  private List<PricePoint> fetchRangeOrChunked(String assetId, Instant start, Instant end) {
    Duration total = Duration.between(start, end);
    if (total.compareTo(MAX_CHUNK) > 0) {
      return fetchChunked(assetId, start, end);
    }
    return fetchRange(assetId, start, end);
  }

  private List<PricePoint> fetchChunked(String assetId, Instant start, Instant end) {
    log.debug("CoinGecko chunked fetch for {} from {} to {}", assetId, start, end);
    List<PricePoint> all = new ArrayList<>();
    Instant chunkStart = start;
    while (chunkStart.isBefore(end)) {
      Instant chunkEnd = chunkStart.plus(MAX_CHUNK);
      if (chunkEnd.isAfter(end)) {
        chunkEnd = end;
      }
      all.addAll(fetchRange(assetId, chunkStart, chunkEnd));
      chunkStart = chunkEnd.plusSeconds(1);
    }
    // dedup by timestamp, keeping latest value when timestamps collide at chunk boundaries
    LinkedHashMap<Instant, BigDecimal> byTime = new LinkedHashMap<>();
    for (PricePoint p : all) {
      byTime.put(p.time(), p.price());
    }
    return byTime.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new PricePoint(e.getKey(), e.getValue()))
        .toList();
  }

  private List<PricePoint> fetchRange(String assetId, Instant from, Instant to) {
    if (!enabled) {
      throw new MarketDataServerException("CoinGecko historical integration is disabled");
    }
    CoinGeckoMarketChartResponse response =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/coins/{id}/market_chart/range")
                        .queryParam("vs_currency", USD)
                        .queryParam("from", from.getEpochSecond())
                        .queryParam("to", to.getEpochSecond())
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
                        .defaultIfEmpty("CoinGecko error")
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

  private String resolveAssetId(String symbol) {
    String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
    return resolvedCoinIds.computeIfAbsent(
        normalizedSymbol,
        key ->
            assetCatalogQueryPort
                .findAssetIdBySymbol(key)
                .orElseGet(() -> searchCoinGeckoAssetId(key)));
  }

  private String searchCoinGeckoAssetId(String symbol) {
    if (!enabled) {
      throw new MarketDataServerException("CoinGecko historical integration is disabled");
    }
    CoinGeckoSearchResponse response =
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("/search").queryParam("query", symbol).build())
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
                        .defaultIfEmpty("CoinGecko search error")
                        .map(MarketDataServerException::new))
            .bodyToMono(CoinGeckoSearchResponse.class)
            .timeout(responseTimeout)
            .block();

    if (response == null || response.coins() == null || response.coins().isEmpty()) {
      throw new UnknownAssetSymbolException("Unknown asset symbol: " + symbol);
    }
    return response.coins().stream()
        .filter(coin -> coin.id() != null && symbol.equalsIgnoreCase(coin.symbol()))
        .min(Comparator.comparingInt(this::marketRank))
        .or(
            () ->
                response.coins().stream()
                    .filter(coin -> coin.id() != null && symbol.equalsIgnoreCase(coin.id()))
                    .findFirst())
        .orElseThrow(() -> new UnknownAssetSymbolException("Unknown asset symbol: " + symbol))
        .id();
  }

  private BigDecimal price(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private int marketRank(CoinGeckoSearchCoin coin) {
    return coin.marketCapRank() == null ? Integer.MAX_VALUE : coin.marketCapRank();
  }
}
