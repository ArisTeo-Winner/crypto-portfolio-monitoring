package com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MassiveProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Historico de dividendos via Massive ({@code /stocks/v1/dividends?ticker=}). Mapea cada fila del
 * {@code results[]} a {@link DividendEventData} (id, fechas, cash_amount, frequency,
 * distribution_type, split_adjusted_cash_amount, historical_adjustment_factor). Si Massive no esta
 * configurado o falla, devuelve lista vacia (la deteccion continua sin romper).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MassiveDividendAdapter implements DividendProviderPort {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final WebClient webClient;
  private final MassiveProperties massiveProperties;

  @Override
  public List<DividendEventData> fetchDividends(String ticker) {
    if (!StringUtils.hasText(massiveProperties.getBaseUrl())
        || !StringUtils.hasText(massiveProperties.getApiKey())
        || !StringUtils.hasText(ticker)) {
      return List.of();
    }
    String normalized = ticker.trim().toUpperCase();
    String url =
        String.format(
            "%s/stocks/v1/dividends?ticker=%s&limit=1000&apiKey=%s",
            massiveProperties.getBaseUrl(), normalized, massiveProperties.getApiKey());
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> body =
          webClient
              .get()
              .uri(url)
              .exchangeToMono(response -> response.bodyToMono(Map.class))
              .timeout(TIMEOUT)
              .block();
      return parse(body, normalized);
    } catch (WebClientResponseException ex) {
      log.warn(
          "No se pudo obtener dividendos de Massive para {} (HTTP {}); se continua sin deteccion",
          normalized,
          ex.getStatusCode().value());
      return List.of();
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo obtener dividendos de Massive para {} ({}); se continua sin deteccion",
          normalized,
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  List<DividendEventData> parse(Map<String, Object> body, String ticker) {
    if (body == null || !(body.get("results") instanceof List<?> results)) {
      return List.of();
    }
    List<DividendEventData> events = new ArrayList<>();
    for (Object item : results) {
      if (!(item instanceof Map<?, ?> row)) {
        continue;
      }
      LocalDate exDate = parseDate(row.get("ex_dividend_date"));
      BigDecimal cashAmount = toBigDecimal(row.get("cash_amount"));
      if (exDate == null || cashAmount == null) {
        continue; // sin ex-date o monto no es un evento utilizable
      }
      events.add(
          new DividendEventData(
              ticker,
              exDate,
              parseDate(row.get("record_date")),
              parseDate(row.get("pay_date")),
              parseDate(row.get("declaration_date")),
              cashAmount,
              row.get("currency") == null ? null : String.valueOf(row.get("currency")),
              toShort(row.get("frequency")),
              row.get("distribution_type") == null
                  ? null
                  : String.valueOf(row.get("distribution_type")),
              toBigDecimal(row.get("split_adjusted_cash_amount")),
              toBigDecimal(row.get("historical_adjustment_factor")),
              row.get("id") == null ? null : String.valueOf(row.get("id"))));
    }
    return events;
  }

  private LocalDate parseDate(Object value) {
    return value == null ? null : LocalDate.parse(String.valueOf(value));
  }

  private BigDecimal toBigDecimal(Object value) {
    return value == null ? null : new BigDecimal(String.valueOf(value));
  }

  private Short toShort(Object value) {
    return value == null ? null : (short) Double.valueOf(String.valueOf(value)).intValue();
  }
}
