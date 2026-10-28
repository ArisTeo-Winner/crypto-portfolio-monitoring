package com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayData;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MassiveProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Calendario US próximo via Massive ({@code /v1/marketstatus/upcoming}) — primario (ADR-0012, F4).
 * Da NYSE/NASDAQ con {@code status} closed|early-close y {@code open}/{@code close} en instantes
 * UTC (la hora de cierre anticipado se convierte a hora local ET). Vacío si no configurado o falla.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class MassiveUpcomingHolidayAdapter implements MarketHolidayProviderPort {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final WebClient webClient;
  private final MassiveProperties massiveProperties;

  @Override
  public String providerName() {
    return "MASSIVE_UPCOMING";
  }

  @Override
  public List<MarketHolidayData> fetchHolidays() {
    if (!StringUtils.hasText(massiveProperties.getBaseUrl())
        || !StringUtils.hasText(massiveProperties.getApiKey())) {
      return List.of();
    }
    String url =
        String.format(
            "%s/v1/marketstatus/upcoming?apiKey=%s",
            massiveProperties.getBaseUrl(), massiveProperties.getApiKey());
    try {
      List<?> body =
          webClient
              .get()
              .uri(url)
              .exchangeToMono(response -> response.bodyToMono(List.class))
              .timeout(TIMEOUT)
              .block();
      return parse(body);
    } catch (RuntimeException ex) {
      // No logueamos la excepción cruda: su mensaje incluye la URI con el apiKey (se filtraría).
      log.warn(
          "No se pudo obtener el calendario US de Massive ({}); se intentará el fallback",
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  private List<MarketHolidayData> parse(List<?> body) {
    if (body == null) {
      return List.of();
    }
    List<MarketHolidayData> result = new ArrayList<>();
    Set<LocalDate> seen = new LinkedHashSet<>();
    for (Object item : body) {
      if (!(item instanceof java.util.Map<?, ?> row)) {
        continue;
      }
      // NYSE y NASDAQ son idénticos para equity US: nos quedamos con una sola fila por fecha.
      if (!"NYSE".equalsIgnoreCase(String.valueOf(row.get("exchange")))) {
        continue;
      }
      LocalDate date = parseDate(row.get("date"));
      if (date == null || !seen.add(date)) {
        continue;
      }
      String status = String.valueOf(row.get("status"));
      LocalTime earlyClose =
          "early-close".equalsIgnoreCase(status) ? parseEtLocalTime(row.get("close")) : null;
      String name = row.get("name") == null ? "Holiday" : String.valueOf(row.get("name"));
      result.add(new MarketHolidayData(Market.US_EQUITY, date, name, earlyClose));
    }
    return result;
  }

  private LocalDate parseDate(Object value) {
    return value == null ? null : LocalDate.parse(String.valueOf(value));
  }

  private LocalTime parseEtLocalTime(Object isoInstant) {
    if (isoInstant == null) {
      return null;
    }
    return Instant.parse(String.valueOf(isoInstant)).atZone(Market.US_EQUITY.zone()).toLocalTime();
  }
}
