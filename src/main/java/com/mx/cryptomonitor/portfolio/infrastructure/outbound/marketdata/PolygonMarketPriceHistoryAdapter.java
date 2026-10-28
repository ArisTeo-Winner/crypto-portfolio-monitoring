package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

@Component
@ConditionalOnExpression("'${polygon.base-url:}' != '' and '${polygon.api.key:}' != ''")
@Order(10)
public class PolygonMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;

  public PolygonMarketPriceHistoryAdapter(
      WebClient.Builder webClientBuilder,
      @Value("${polygon.base-url}") String baseUrl,
      @Value("${polygon.api.key}") String apiKey) {
    this.webClient = webClientBuilder.build();
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.apiKey = apiKey;
  }

  @Override
  public boolean supports(AssetType assetType) {
    return assetType == AssetType.STOCK
        || assetType == AssetType.ETF
        || assetType == AssetType.INDEX;
  }

  @Override
  public List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, HoldingsHistoryRange range) {
    LocalDate to = LocalDate.now(ZoneOffset.UTC);
    LocalDate from = range.isAll() ? LocalDate.of(2000, 1, 1) : to.minus(range.period());
    Map<String, Object> json =
        webClient
            .get()
            .uri(
                "%s/v2/aggs/ticker/%s/range/1/day/%s/%s?adjusted=true&sort=asc&limit=50000&apiKey=%s"
                    .formatted(baseUrl, symbol.trim().toUpperCase(), from, to, apiKey))
            .retrieve()
            .bodyToMono(Map.class)
            .map(Map.class::cast)
            .block();
    return extractSeries(json);
  }

  private List<PricePoint> extractSeries(Map<String, Object> json) {
    if (json == null || !(json.get("results") instanceof List<?> results)) {
      return List.of();
    }
    return results.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(this::toPoint)
        .sorted(Comparator.comparing(PricePoint::time))
        .toList();
  }

  private PricePoint toPoint(Map<?, ?> result) {
    Object timestamp = result.get("t");
    Object close = result.get("c");
    long epochMillis = timestamp == null ? 0L : Long.parseLong(String.valueOf(timestamp));
    BigDecimal price = close == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(close));
    return new PricePoint(java.time.Instant.ofEpochMilli(epochMillis), price);
  }
}
