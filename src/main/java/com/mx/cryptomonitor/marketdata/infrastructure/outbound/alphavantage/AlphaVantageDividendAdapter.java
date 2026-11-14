package com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderUnavailableException;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.AlphaVantageProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Proveedor de dividendos US vía AlphaVantage ({@code GET /query?function=DIVIDENDS}) — fallback
 * del leg US (detrás de Massive). Mapea {@code data[]} a {@link DividendEventData}: {@code amount}
 * → {@code cashAmount}, {@code ex_dividend_date}/{@code payment_date}/{@code record_date}/{@code
 * declaration_date}. Moneda USD; {@code distribution_type} recurring por defecto.
 *
 * <p><b>Filtro de calidad OBLIGATORIO:</b> AlphaVantage incluye filas-artefacto (p. ej. GOOGL
 * {@code 2014-04-03} con {@code amount=567.97} y fechas {@code "None"}) que NO son dividendos y
 * dispararían el DPS del año. Se descartan las filas sin {@code payment_date} válido (las reales
 * siempre lo traen), lo que elimina esos artefactos sin tocar los dividendos legítimos.
 *
 * <p>Ante error/cuota (HTTP 200 con {@code {"Information"|"Note"|"Error Message": ...}}) no hay
 * {@code data} → devuelve lista vacía (la ingesta continúa). El {@code apikey} viaja como query
 * param pero la URL NUNCA se loggea (solo contexto/clase), de modo que no aparece en logs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlphaVantageDividendAdapter implements DividendProviderPort {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final ParameterizedTypeReference<Map<String, Object>> BODY_TYPE =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final AlphaVantageProperties properties;

  @Override
  public List<DividendEventData> fetchDividends(String ticker) {
    if (!StringUtils.hasText(properties.getBaseUrl())
        || !StringUtils.hasText(properties.getApiKey())
        || !StringUtils.hasText(ticker)) {
      return List.of();
    }
    String symbol = ticker.trim().toUpperCase(Locale.ROOT);
    URI uri =
        UriComponentsBuilder.fromUriString(properties.getBaseUrl().replaceAll("/$", ""))
            .path("/query")
            .queryParam("function", "DIVIDENDS")
            .queryParam("symbol", symbol)
            .queryParam("apikey", properties.getApiKey())
            .build()
            .toUri();
    try {
      Map<String, Object> body =
          webClient.get().uri(uri).retrieve().bodyToMono(BODY_TYPE).timeout(TIMEOUT).block();
      // Envelope sin 'data' (Information/Note/Error Message) = cuota/error del proveedor: NO
      // concluyente (ADR-0025 F2). Distinto de 'data' vacío, que sí sería "no paga".
      if (body != null && !(body.get("data") instanceof List)) {
        log.warn("AlphaVantage respondió envelope de cuota/error para {}; no concluyente", symbol);
        throw new DividendProviderUnavailableException(
            "AlphaVantage " + symbol + ": envelope de cuota/error");
      }
      return parse(body, symbol);
    } catch (DividendProviderUnavailableException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo obtener dividendos de AlphaVantage para {} ({}); no concluyente",
          symbol,
          ex.getClass().getSimpleName());
      throw new DividendProviderUnavailableException(
          "AlphaVantage " + symbol + ": " + ex.getClass().getSimpleName());
    }
  }

  List<DividendEventData> parse(Map<String, Object> body, String symbol) {
    if (body == null || !(body.get("data") instanceof List<?> rows)) {
      return List.of(); // sin 'data': envelope de error/cuota (Information/Note/Error Message)
    }
    List<DividendEventData> events = new ArrayList<>();
    for (Object item : rows) {
      if (!(item instanceof Map<?, ?> row)) {
        continue;
      }
      LocalDate exDate = parseDate(row.get("ex_dividend_date"));
      LocalDate payDate = parseDate(row.get("payment_date"));
      BigDecimal amount = toBigDecimal(row.get("amount"));
      // Filtro de artefactos: sin payment_date valido o sin monto positivo => no es un dividendo.
      if (exDate == null || payDate == null || amount == null || amount.signum() <= 0) {
        continue;
      }
      events.add(
          new DividendEventData(
              symbol,
              exDate,
              parseDate(row.get("record_date")),
              payDate,
              parseDate(row.get("declaration_date")),
              amount,
              "USD",
              null,
              "recurring",
              null,
              null,
              "alphavantage:" + symbol + ":" + exDate));
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
}
