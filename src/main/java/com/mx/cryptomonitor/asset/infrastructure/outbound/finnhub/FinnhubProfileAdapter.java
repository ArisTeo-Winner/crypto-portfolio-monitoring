package com.mx.cryptomonitor.asset.infrastructure.outbound.finnhub;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.mx.cryptomonitor.asset.application.port.out.StockProfilePort;

import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador Finnhub /stock/profile2 — fuente unificada de logo real y market cap para el catalogo
 * de STOCK (reemplaza el screener restringido de FMP).
 */
@Component
@Slf4j
public class FinnhubProfileAdapter implements StockProfilePort {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final WebClient webClient;
  private final String apiKey;

  public FinnhubProfileAdapter(
      @Qualifier("finnhubWebClient") WebClient webClient,
      @Value("${finnhub.api-key:}") String apiKey) {
    this.webClient = webClient;
    this.apiKey = apiKey;
  }

  @Override
  public Optional<StockProfile> getProfile(String symbol) {
    if (apiKey == null || apiKey.isBlank() || symbol == null || symbol.isBlank()) {
      return Optional.empty();
    }

    String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
    try {
      Optional<StockProfile> profile =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/stock/profile2")
                          .queryParam("symbol", normalizedSymbol)
                          .queryParam("token", apiKey)
                          .build())
              .retrieve()
              .bodyToMono(FinnhubProfile2Payload.class)
              .timeout(REQUEST_TIMEOUT)
              .blockOptional()
              .flatMap(response -> toProfile(normalizedSymbol, response));
      return profile;
    } catch (RuntimeException ex) {
      log.warn("Finnhub profile2 request failed for symbol {}: {}", normalizedSymbol, detailOf(ex));
      return Optional.empty();
    }
  }

  private Optional<StockProfile> toProfile(
      String requestedSymbol, FinnhubProfile2Payload response) {
    if (response == null || response.name() == null || response.name().isBlank()) {
      return Optional.empty();
    }

    String symbol =
        response.ticker() == null || response.ticker().isBlank()
            ? requestedSymbol
            : response.ticker().trim().toUpperCase(Locale.ROOT);
    String logoUrl =
        response.logo() == null || response.logo().isBlank() ? null : response.logo().trim();
    Long marketCapMillions =
        response.marketCapitalization() != null
            ? Math.round(response.marketCapitalization())
            : null;
    return Optional.of(
        new StockProfile(
            symbol,
            response.name().trim(),
            logoUrl,
            marketCapMillions,
            response.exchange(),
            response.currency()));
  }

  private static String detailOf(RuntimeException ex) {
    return (ex instanceof WebClientResponseException w)
        ? "HTTP " + w.getStatusCode()
        : ex.getClass().getSimpleName();
  }

  private record FinnhubProfile2Payload(
      String logo,
      String name,
      String ticker,
      Double marketCapitalization,
      String exchange,
      String currency) {}
}
