package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

@Component
@Order(20)
public class AlphaVantageMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private static final String TIME_SERIES_DAILY_KEY = "Time Series (Daily)";

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;

  public AlphaVantageMarketPriceHistoryAdapter(
      WebClient.Builder webClientBuilder,
      @Value("${marketdata.alphavantage.base-url}") String baseUrl,
      @Value("${marketdata.alphavantage.api-key}") String apiKey) {
    this.webClient = webClientBuilder.build();
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.apiKey = apiKey;
  }

  @Override
  public boolean supports(AssetType assetType) {
    return assetType == AssetType.STOCK || assetType == AssetType.ETF || assetType == AssetType.INDEX;
  }

  @Override
  public List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, HoldingsHistoryRange range) {
    Map<String, Object> json =
        webClient
            .get()
            .uri(
                "%s/query?function=TIME_SERIES_DAILY&symbol=%s&outputsize=%s&apikey=%s"
                    .formatted(
                        baseUrl,
                        symbol.trim().toUpperCase(),
                        range.days() > 100 ? "full" : "compact",
                        apiKey))
            .retrieve()
            .bodyToMono(Map.class)
            .map(Map.class::cast)
            .block();

    return extractSeries(json, range);
  }

  private List<PricePoint> extractSeries(Map<String, Object> json, HoldingsHistoryRange range) {
    if (json == null) {
      return List.of();
    }
    throwIfProviderReportedError(json);
    Object rawSeries = json.get(TIME_SERIES_DAILY_KEY);
    if (!(rawSeries instanceof Map<?, ?> series)) {
      return List.of();
    }

    LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(range.days());
    return series.entrySet().stream()
        .filter(entry -> entry.getKey() != null && entry.getValue() instanceof Map<?, ?>)
        .map(entry -> toPoint(String.valueOf(entry.getKey()), (Map<?, ?>) entry.getValue()))
        .filter(point -> !point.time().atZone(ZoneOffset.UTC).toLocalDate().isBefore(cutoff))
        .sorted(Comparator.comparing(PricePoint::time))
        .toList();
  }

  private PricePoint toPoint(String date, Map<?, ?> values) {
    Object close = values.get("4. close");
    if (close == null) {
      close = values.get("5. adjusted close");
    }
    return new PricePoint(
        LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC),
        close == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(close)));
  }

  private void throwIfProviderReportedError(Map<String, Object> json) {
    if (json.containsKey("Note")) {
      throw new MarketDataRateLimitException(String.valueOf(json.get("Note")));
    }
    if (json.containsKey("Information")) {
      throw new MarketDataServerException(String.valueOf(json.get("Information")));
    }
    if (json.containsKey("Error Message")) {
      throw new UnknownAssetSymbolException(String.valueOf(json.get("Error Message")));
    }
  }
}
