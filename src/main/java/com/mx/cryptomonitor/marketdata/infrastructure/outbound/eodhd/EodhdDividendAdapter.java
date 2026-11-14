package com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.EodhdProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Histórico de dividendos vía EODHD ({@code GET /api/div/{symbol}}) — addendum al ADR-0016: EODHD
 * cubre dividendos en los DOS mercados, incluido BMV/SIC en MXN nativo ({@code {EMISORA}.MX}), que
 * el proveedor US (Massive) no sirve. Mapea cada fila a {@link DividendEventData}: {@code value}
 * (split-adjusted) → {@code splitAdjustedCashAmount} (lo que consume el crecimiento de dividendo),
 * {@code unadjustedValue} → {@code cashAmount} (DPS bruto), {@code period} → frecuencia.
 *
 * <p>El símbolo almacenado es el de ENTRADA (sin sufijo EOD), para que coincida con lo que consulta
 * {@code dividend-growth}; el sufijo ({@code .US}/{@code .MX}) se aplica solo en la llamada a
 * EODHD.
 *
 * <p>Seguridad: el símbolo viaja como segmento de path codificado ({@link UriComponentsBuilder}) y
 * validado contra un allowlist; el {@code api_token} viaja como query param pero la URL NUNCA se
 * loggea (solo status/clase de excepción), de modo que el token no aparece en logs. Si EODHD no
 * está configurado o falla, devuelve lista vacía (la detección continúa sin romper, igual que
 * Massive).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EodhdDividendAdapter implements DividendProviderPort {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String HISTORY_START = "2000-01-01";
  private static final Pattern SYMBOL_ALLOWLIST =
      Pattern.compile("^[A-Z0-9_\\-]{1,20}(\\.[A-Z]{1,4})?$");
  private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final EodhdProperties eodhdProperties;

  @Override
  public List<DividendEventData> fetchDividends(String ticker) {
    if (!StringUtils.hasText(eodhdProperties.getBaseUrl())
        || !StringUtils.hasText(eodhdProperties.getApiToken())
        || !StringUtils.hasText(ticker)) {
      return List.of();
    }
    String normalized = ticker.trim().toUpperCase(Locale.ROOT);
    String eodSymbol = toEodSymbol(normalized);
    if (!SYMBOL_ALLOWLIST.matcher(eodSymbol).matches()) {
      return List.of();
    }

    URI uri =
        UriComponentsBuilder.fromUriString(eodhdProperties.getBaseUrl().replaceAll("/$", ""))
            .path("/api/div/{symbol}")
            .queryParam("api_token", eodhdProperties.getApiToken())
            .queryParam("fmt", "json")
            .queryParam("from", HISTORY_START)
            .build(eodSymbol);

    try {
      List<Map<String, Object>> rows =
          webClient.get().uri(uri).retrieve().bodyToMono(LIST_TYPE).timeout(TIMEOUT).block();
      return parse(rows, normalized);
    } catch (WebClientResponseException ex) {
      log.warn(
          "No se pudo obtener dividendos de EODHD para {} (HTTP {}); se continua sin deteccion",
          normalized,
          ex.getStatusCode().value());
      return List.of();
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo obtener dividendos de EODHD para {} ({}); se continua sin deteccion",
          normalized,
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  List<DividendEventData> parse(List<Map<String, Object>> rows, String ticker) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<DividendEventData> events = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      LocalDate exDate = parseDate(row.get("date"));
      BigDecimal adjusted = toBigDecimal(row.get("value"));
      BigDecimal unadjusted = toBigDecimal(row.get("unadjustedValue"));
      BigDecimal gross = unadjusted != null ? unadjusted : adjusted;
      if (exDate == null || gross == null) {
        continue; // sin ex-date o monto no es un evento utilizable
      }
      events.add(
          new DividendEventData(
              ticker,
              exDate,
              parseDate(row.get("recordDate")),
              parseDate(row.get("paymentDate")),
              parseDate(row.get("declarationDate")),
              gross,
              row.get("currency") == null ? null : String.valueOf(row.get("currency")),
              toFrequency(row.get("period")),
              "recurring",
              adjusted != null ? adjusted : gross,
              null,
              "eodhd:" + ticker + ":" + exDate));
    }
    return events;
  }

  private String toEodSymbol(String symbol) {
    String suffix =
        (eodhdProperties.getDefaultSuffix() == null || eodhdProperties.getDefaultSuffix().isBlank())
            ? ".US"
            : eodhdProperties.getDefaultSuffix();
    return symbol.contains(".") ? symbol : symbol + suffix;
  }

  private LocalDate parseDate(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    if (raw.isEmpty() || "0000-00-00".equals(raw)) {
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
    if (raw.isEmpty() || "NA".equalsIgnoreCase(raw) || "null".equalsIgnoreCase(raw)) {
      return null;
    }
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Short toFrequency(Object period) {
    if (period == null) {
      return null;
    }
    return switch (String.valueOf(period).trim().toLowerCase(Locale.ROOT)) {
      case "weekly" -> (short) 52;
      case "monthly" -> (short) 12;
      case "quarterly" -> (short) 4;
      case "semiannual", "semi-annual" -> (short) 2;
      case "annual", "yearly" -> (short) 1;
      default -> null;
    };
  }
}
