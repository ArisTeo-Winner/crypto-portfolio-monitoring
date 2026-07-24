package com.mx.cryptomonitor.asset.infrastructure.outbound.finnhub;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.mx.cryptomonitor.asset.application.port.out.IpoCalendarPort;

import lombok.extern.slf4j.Slf4j;

/** Adaptador Finnhub /calendar/ipo — detecta nuevos listados en bolsa. */
@Component
@Slf4j
public class FinnhubIpoCalendarAdapter implements IpoCalendarPort {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final WebClient webClient;
  private final String apiKey;

  public FinnhubIpoCalendarAdapter(
      @Qualifier("finnhubWebClient") WebClient webClient,
      @Value("${finnhub.api-key:}") String apiKey) {
    this.webClient = webClient;
    this.apiKey = apiKey;
  }

  @Override
  public List<IpoEntry> getRecentIpos(LocalDate from, LocalDate to) {
    if (apiKey == null || apiKey.isBlank()) {
      return List.of();
    }

    try {
      FinnhubIpoCalendarPayload response =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/calendar/ipo")
                          .queryParam("from", from)
                          .queryParam("to", to)
                          .queryParam("token", apiKey)
                          .build())
              .retrieve()
              .bodyToMono(FinnhubIpoCalendarPayload.class)
              .timeout(REQUEST_TIMEOUT)
              .block();

      if (response == null || response.ipoCalendar() == null) {
        return List.of();
      }
      return response.ipoCalendar().stream().map(this::toEntry).filter(Objects::nonNull).toList();
    } catch (RuntimeException ex) {
      log.warn("Finnhub IPO calendar request failed: {}", detailOf(ex));
      return List.of();
    }
  }

  private IpoEntry toEntry(FinnhubIpoItem item) {
    if (item == null || item.symbol() == null || item.symbol().isBlank()) {
      return null;
    }
    return new IpoEntry(
        item.symbol().trim().toUpperCase(Locale.ROOT),
        item.name(),
        item.exchange(),
        parseDate(item.date()),
        item.status(),
        item.totalSharesValue());
  }

  private LocalDate parseDate(String raw) {
    try {
      return raw == null || raw.isBlank() ? null : LocalDate.parse(raw);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  private static String detailOf(RuntimeException ex) {
    return (ex instanceof WebClientResponseException w)
        ? "HTTP " + w.getStatusCode()
        : ex.getClass().getSimpleName();
  }

  private record FinnhubIpoCalendarPayload(List<FinnhubIpoItem> ipoCalendar) {}

  private record FinnhubIpoItem(
      String symbol,
      String name,
      String exchange,
      String date,
      String status,
      Long totalSharesValue) {}
}
