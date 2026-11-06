package com.mx.cryptomonitor.marketdata.infrastructure.outbound.fmp;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Proveedor de dividendos US vía Financial Modeling Prep ({@code GET /stable/dividends?symbol=}) —
 * fallback del leg US (detrás de Massive, delante de AlphaVantage). Es el más completo de los tres
 * US: incluye el dividendo futuro ya anunciado (p. ej. GOOGL ex {@code 2026-12-07}, que
 * AlphaVantage omite), sin filas-artefacto, y con {@code frequency}. Mapea cada fila a {@link
 * DividendEventData}: {@code dividend} → {@code cashAmount}, {@code adjDividend} → {@code
 * splitAdjustedCashAmount}, {@code date} = ex-dividendo, {@code paymentDate}/{@code
 * recordDate}/{@code declarationDate}.
 *
 * <p>Reusa la config global {@code fmp.base-url}/{@code fmp.api-key} (misma que el catálogo, sin
 * importar clases de otro módulo). Ante error/plan/cuota (401/403 o cuerpo no-lista) devuelve lista
 * vacía (la ingesta continúa). El {@code apikey} viaja como query param pero la URL NUNCA se loggea
 * (solo contexto/clase), de modo que no aparece en logs.
 */
@Component
@Slf4j
public class FmpDividendAdapter implements DividendProviderPort {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final String apiKey;

  public FmpDividendAdapter(
      WebClient.Builder builder,
      @Value("${fmp.base-url:https://financialmodelingprep.com}") String baseUrl,
      @Value("${fmp.api-key:}") String apiKey) {
    this.webClient = builder.baseUrl(baseUrl.replaceAll("/$", "")).build();
    this.apiKey = apiKey;
  }

  @Override
  public List<DividendEventData> fetchDividends(String ticker) {
    if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(ticker)) {
      return List.of();
    }
    String symbol = ticker.trim().toUpperCase(Locale.ROOT);
    try {
      List<Map<String, Object>> rows =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/stable/dividends")
                          .queryParam("symbol", symbol)
                          .queryParam("apikey", apiKey)
                          .build())
              .retrieve()
              .bodyToMono(LIST_TYPE)
              .timeout(TIMEOUT)
              .block();
      return parse(rows, symbol);
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo obtener dividendos de FMP para {} ({}); se continua sin deteccion",
          symbol,
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  List<DividendEventData> parse(List<Map<String, Object>> rows, String symbol) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<DividendEventData> events = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      LocalDate exDate = parseDate(row.get("date"));
      BigDecimal dividend = toBigDecimal(row.get("dividend"));
      if (exDate == null || dividend == null || dividend.signum() <= 0) {
        continue;
      }
      events.add(
          new DividendEventData(
              symbol,
              exDate,
              parseDate(row.get("recordDate")),
              parseDate(row.get("paymentDate")),
              parseDate(row.get("declarationDate")),
              dividend,
              "USD",
              toFrequency(row.get("frequency")),
              "recurring",
              toBigDecimal(row.get("adjDividend")),
              null,
              "fmp:" + symbol + ":" + exDate));
    }
    return events;
  }

  private LocalDate parseDate(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    if (raw.isEmpty() || "None".equalsIgnoreCase(raw) || "null".equalsIgnoreCase(raw)) {
      return null;
    }
    try {
      return LocalDate.parse(raw);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    if (raw.isEmpty() || "None".equalsIgnoreCase(raw) || "null".equalsIgnoreCase(raw)) {
      return null;
    }
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Short toFrequency(Object frequency) {
    if (frequency == null) {
      return null;
    }
    return switch (String.valueOf(frequency).trim().toLowerCase(Locale.ROOT)) {
      case "weekly" -> (short) 52;
      case "monthly" -> (short) 12;
      case "quarterly" -> (short) 4;
      case "semi-annual", "semiannual" -> (short) 2;
      case "annual", "yearly" -> (short) 1;
      default -> null;
    };
  }
}
