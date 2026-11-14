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
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

/**
 * Proveedor de histórico STOCK/ETF/INDEX vía Massive (antes Polygon.io; rebrand a massive.com el
 * 2025-10-30, mismas keys y endpoints). Usa el endpoint de agregados {@code
 * /v2/aggs/ticker/{ticker}/range/1/day/{from}/{to}} con {@code adjusted=true} (feed ajustado ⇒ se
 * ajusta la CANTIDAD, no el precio; ADR-0011).
 *
 * <p>Comparte la MISMA credencial que el resto del módulo marketdata ({@code marketdata.massive.*}
 * / {@code MASSIVE_API_KEY} + {@code API_MASSIVE_BASE_URL}); no hay un par {@code polygon.*}
 * separado. El token viaja como query param {@code apiKey}; NUNCA se loggea la URL con token.
 * 429/5xx y cualquier otro no-2xx (p. ej. 403/451 de geobloqueo) se mapean a excepciones
 * recuperables (ADR-0021) para que {@code CachedMarketPriceHistoryAdapter} caiga al siguiente
 * proveedor.
 */
@Component
@ConditionalOnExpression(
    "'${marketdata.massive.base-url:}' != '' and '${marketdata.massive.api-key:}' != ''")
@Order(10)
public class MassiveMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;

  public MassiveMarketPriceHistoryAdapter(
      WebClient.Builder webClientBuilder,
      @Value("${marketdata.massive.base-url}") String baseUrl,
      @Value("${marketdata.massive.api-key}") String apiKey) {
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
            .onStatus(
                status -> status.value() == 429,
                resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("Massive rate limit exceeded")
                        .map(MarketDataRateLimitException::new))
            .onStatus(
                HttpStatusCode::is5xxServerError,
                resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("Massive server error")
                        .map(body -> new MarketDataServerException("Massive error: " + body)))
            .onStatus(
                HttpStatusCode::isError,
                resp -> MarketDataResponseErrors.recoverable("Massive", resp))
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
