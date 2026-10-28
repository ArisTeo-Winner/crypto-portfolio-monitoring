package com.mx.cryptomonitor.marketdata.infrastructure.outbound.finnhub;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayData;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.Market;

import lombok.extern.slf4j.Slf4j;

/**
 * Calendario US via Finnhub ({@code /stock/market-holiday?exchange=US}) — fallback multi-año
 * (ADR-0012, F4). {@code tradingHour} vacío = cierre total; "09:30-13:00" = medio día (cierre a la
 * hora tras el guion). Token leído de {@code marketdata.finnhub.token} (nunca hardcodeado).
 */
@Component
@Order(2)
@Slf4j
public class FinnhubHolidayAdapter implements MarketHolidayProviderPort {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final WebClient webClient;
  private final String baseUrl;
  private final String token;

  public FinnhubHolidayAdapter(
      WebClient webClient,
      @Value("${marketdata.finnhub.base-url:https://finnhub.io/api/v1}") String baseUrl,
      @Value("${marketdata.finnhub.token:}") String token) {
    this.webClient = webClient;
    this.baseUrl = baseUrl;
    this.token = token;
  }

  @Override
  public String providerName() {
    return "FINNHUB";
  }

  @Override
  public List<MarketHolidayData> fetchHolidays() {
    if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(token)) {
      return List.of();
    }
    // Token en header (X-Finnhub-Token), nunca en la URL, para que no se filtre a logs/proxies.
    String url = baseUrl + "/stock/market-holiday?exchange=US";
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> body =
          webClient
              .get()
              .uri(url)
              .header("X-Finnhub-Token", token)
              .exchangeToMono(response -> response.bodyToMono(Map.class))
              .timeout(TIMEOUT)
              .block();
      return parse(body);
    } catch (RuntimeException ex) {
      log.warn("No se pudo obtener el calendario US de Finnhub: {}", ex.getClass().getSimpleName());
      return List.of();
    }
  }

  private List<MarketHolidayData> parse(Map<String, Object> body) {
    if (body == null || !(body.get("data") instanceof List<?> data)) {
      return List.of();
    }
    List<MarketHolidayData> result = new ArrayList<>();
    for (Object item : data) {
      if (!(item instanceof Map<?, ?> row)) {
        continue;
      }
      LocalDate date = parseDate(row.get("atDate"));
      if (date == null) {
        continue;
      }
      String name = row.get("eventName") == null ? "Holiday" : String.valueOf(row.get("eventName"));
      LocalTime earlyClose = parseEarlyClose(row.get("tradingHour"));
      result.add(new MarketHolidayData(Market.US_EQUITY, date, name, earlyClose));
    }
    return result;
  }

  private LocalDate parseDate(Object value) {
    return value == null ? null : LocalDate.parse(String.valueOf(value));
  }

  // tradingHour: "" = cierre total (null); "09:30-13:00" = medio día => 13:00.
  private LocalTime parseEarlyClose(Object tradingHour) {
    String value = tradingHour == null ? "" : String.valueOf(tradingHour).trim();
    int dash = value.indexOf('-');
    if (dash < 0 || dash + 1 >= value.length()) {
      return null;
    }
    try {
      return LocalTime.parse(value.substring(dash + 1).trim(), HH_MM);
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
