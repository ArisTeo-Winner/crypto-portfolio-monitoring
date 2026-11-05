package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

/**
 * Proveedor PRIMARIO de histórico STOCK/ETF/INDEX vía EODHD EOD ({@code GET /api/eod/{symbol}}) —
 * addendum al ADR-0016: EODHD también sirve la SERIE, no solo el precio de hoy, unificando la
 * fuente de precio+histórico en un solo contrato. Cubre US ({@code {TICKER}.US}) y, respetando un
 * sufijo explícito, BMV/SIC ({@code {EMISORA_SERIE}.MX}, MXN nativo). El ruteo automático
 * símbolo→mercado (ej. {@code MELI}→{@code MELIN.MX}) es la Fase 2 del catálogo ([[ADR-0008]]);
 * aquí un símbolo sin sufijo cae a {@code .US} y uno con sufijo se respeta.
 *
 * <p>Usa {@code adjusted_close} (feed {@code adjusted=true} ⇒ se ajusta la CANTIDAD, no el precio;
 * ADR-0011); si falta, cae a {@code close}. El token viaja como query param {@code api_token};
 * NUNCA se loggea la URL con token.
 *
 * <p>Como provider PRIMARIO de la cadena, ante símbolo no cubierto (HTTP 404 o arreglo vacío) lanza
 * {@link UnknownAssetSymbolException} para que {@code CachedMarketPriceHistoryAdapter} caiga al
 * siguiente proveedor (TwelveData → Massive → AlphaVantage) en vez de cortar con una serie vacía.
 * 429/5xx y cualquier otro no-2xx (p. ej. 403/451 de geobloqueo) se mapean a excepciones
 * recuperables (ADR-0021) para el mismo fallback.
 */
@Component
@ConditionalOnExpression("'${eodhd.base-url:}' != '' and '${eodhd.api-token:}' != ''")
@Order(4)
public class EodhdMarketPriceHistoryProvider implements MarketPriceHistoryProvider {

  private static final LocalDate ALL_START = LocalDate.of(2000, 1, 1);

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiToken;
  private final String defaultSuffix;

  public EodhdMarketPriceHistoryProvider(
      WebClient.Builder webClientBuilder,
      @Value("${eodhd.base-url:https://eodhd.com}") String baseUrl,
      @Value("${eodhd.api-token:}") String apiToken,
      @Value("${eodhd.default-suffix:.US}") String defaultSuffix) {
    this.webClient = webClientBuilder.build();
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.apiToken = apiToken;
    this.defaultSuffix = (defaultSuffix == null || defaultSuffix.isBlank()) ? ".US" : defaultSuffix;
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
    LocalDate from = range.isAll() ? ALL_START : to.minus(range.period());
    String eodSymbol = toEodSymbol(symbol);
    String url =
        String.format(
            "%s/api/eod/%s?api_token=%s&fmt=json&period=d&from=%s&to=%s",
            baseUrl, eodSymbol, apiToken, from, to);

    List<Map<String, Object>> rows =
        webClient
            .get()
            .uri(url)
            .retrieve()
            .onStatus(
                status -> status.value() == 404,
                resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(
                            body ->
                                new UnknownAssetSymbolException(
                                    "EODHD sin histórico para " + eodSymbol)))
            .onStatus(
                status -> status.value() == 429,
                resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("EODHD rate limit exceeded")
                        .map(MarketDataRateLimitException::new))
            .onStatus(
                HttpStatusCode::is5xxServerError,
                resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("EODHD server error")
                        .map(body -> new MarketDataServerException("EODHD error: " + body)))
            .onStatus(
                HttpStatusCode::isError,
                resp -> MarketDataResponseErrors.recoverable("EODHD", resp))
            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .block();

    if (rows == null || rows.isEmpty()) {
      // Símbolo válido pero sin serie en EODHD: deja que la cadena pruebe el siguiente proveedor.
      throw new UnknownAssetSymbolException("EODHD devolvió serie vacía para " + eodSymbol);
    }
    return rows.stream()
        .map(this::toPoint)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(PricePoint::time))
        .toList();
  }

  private PricePoint toPoint(Map<String, Object> row) {
    if (row == null || row.get("date") == null) {
      return null;
    }
    Object adjusted = row.get("adjusted_close");
    BigDecimal price = toBigDecimal(adjusted != null ? adjusted : row.get("close"));
    if (price == null || price.signum() <= 0) {
      return null;
    }
    Instant time =
        LocalDate.parse(String.valueOf(row.get("date"))).atStartOfDay().toInstant(ZoneOffset.UTC);
    return new PricePoint(time, price);
  }

  private BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    if (raw.isEmpty() || "NA".equalsIgnoreCase(raw) || "null".equalsIgnoreCase(raw)) {
      return null;
    }
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String toEodSymbol(String symbol) {
    String normalized = symbol.trim().toUpperCase(Locale.ROOT);
    return normalized.contains(".") ? normalized : normalized + defaultSuffix;
  }
}
