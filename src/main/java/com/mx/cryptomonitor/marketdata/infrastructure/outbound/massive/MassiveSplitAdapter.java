package com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitProviderPort;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MassiveProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Historico de splits via Massive ({@code /stocks/v1/splits?ticker=}). shareMultiplier =
 * split_to/split_from (cubre forward/reverse/stock_dividend). Si Massive no esta configurado o
 * falla, devuelve lista vacia (la valuacion no ajusta, sin romper).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MassiveSplitAdapter implements StockSplitProviderPort {

  private static final int MULTIPLIER_SCALE = 10;
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final WebClient webClient;
  private final MassiveProperties massiveProperties;

  @Override
  public List<StockSplitData> fetchSplits(String ticker) {
    if (!StringUtils.hasText(massiveProperties.getBaseUrl())
        || !StringUtils.hasText(massiveProperties.getApiKey())
        || !StringUtils.hasText(ticker)) {
      return List.of();
    }
    String normalized = ticker.trim().toUpperCase();
    String url =
        String.format(
            "%s/stocks/v1/splits?ticker=%s&limit=1000&sort=execution_date.desc&apiKey=%s",
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
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo obtener splits de Massive para {}; se continua sin ajuste", normalized, ex);
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private List<StockSplitData> parse(Map<String, Object> body, String ticker) {
    if (body == null || !(body.get("results") instanceof List<?> results)) {
      return List.of();
    }
    List<StockSplitData> splits = new ArrayList<>();
    for (Object item : results) {
      if (!(item instanceof Map<?, ?> row)) {
        continue;
      }
      LocalDate executionDate = parseDate(row.get("execution_date"));
      BigDecimal from = toBigDecimal(row.get("split_from"));
      BigDecimal to = toBigDecimal(row.get("split_to"));
      if (executionDate == null || from == null || to == null || from.signum() <= 0) {
        continue;
      }
      BigDecimal multiplier = to.divide(from, MULTIPLIER_SCALE, RoundingMode.HALF_UP);
      splits.add(new StockSplitData(ticker, executionDate, multiplier));
    }
    return splits;
  }

  private LocalDate parseDate(Object value) {
    return value == null ? null : LocalDate.parse(String.valueOf(value));
  }

  private BigDecimal toBigDecimal(Object value) {
    return value == null ? null : new BigDecimal(String.valueOf(value));
  }
}
