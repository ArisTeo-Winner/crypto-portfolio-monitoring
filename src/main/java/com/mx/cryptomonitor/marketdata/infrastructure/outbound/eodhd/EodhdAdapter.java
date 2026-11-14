package com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

/**
 * Adaptador EODHD real-time ({@code GET /api/real-time/{symbol}}) — precio de HOY multi-mercado por
 * sufijo (ADR-0016). Fase 1 resuelve US: {@code {TICKER}{defaultSuffix}} (p.ej. {@code MELI.US},
 * USD de NASDAQ). Si el simbolo ya trae sufijo ({@code MELIN.MX}) se respeta (preparado para la
 * Fase 2, MXN nativo de BMV). El token viaja como query param {@code api_token}; NUNCA se loggea la
 * URL con token. Ante simbolo desconocido (404 / {@code close=NA}) devuelve vacio para permitir el
 * fallback del orquestador; 429 y errores de servidor se mapean a excepciones recuperables.
 */
@Slf4j
public class EodhdAdapter implements StockQuoteProvider {

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiToken;
  private final String defaultSuffix;

  public EodhdAdapter(WebClient webClient, String baseUrl, String apiToken, String defaultSuffix) {
    this.webClient = webClient;
    this.baseUrl = baseUrl;
    this.apiToken = apiToken;
    this.defaultSuffix = (defaultSuffix == null || defaultSuffix.isBlank()) ? ".US" : defaultSuffix;
  }

  @Override
  public String providerName() {
    return "eodhd";
  }

  @Override
  public Optional<BigDecimal> getLatest(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      return Optional.empty();
    }
    String eodSymbol = toEodSymbol(symbol);
    String url =
        String.format("%s/api/real-time/%s?api_token=%s&fmt=json", baseUrl, eodSymbol, apiToken);

    return webClient
        .get()
        .uri(url)
        .exchangeToMono(
            response -> {
              HttpStatusCode status = response.statusCode();
              if (status.is2xxSuccessful()) {
                return response.bodyToMono(Map.class).map(this::extractClose);
              }
              if (status.value() == 404) {
                return response.releaseBody().thenReturn(Optional.<BigDecimal>empty());
              }
              if (status.value() == 429) {
                return response
                    .releaseBody()
                    .then(
                        Mono.error(
                            new ExternalProviderRateLimitException(
                                providerName(), "EODHD alcanzo su limite de peticiones")));
              }
              return response
                  .releaseBody()
                  .then(
                      Mono.error(
                          new ExternalProviderUpstreamException(
                              providerName(), "EODHD respondio HTTP " + status.value())));
            })
        .onErrorMap(
            ex -> !isKnownProviderException(ex),
            ex ->
                new ExternalProviderUpstreamException(
                    providerName(), "No se pudo consultar EODHD para: " + eodSymbol, ex))
        .defaultIfEmpty(Optional.empty())
        .block();
  }

  @Override
  public Optional<BigDecimal> getHistorical(String symbol, LocalDate date) {
    log.info(
        "EODHD historical quote aun no implementado para {} en {}. Se retorna vacio para permitir"
            + " fallback.",
        symbol,
        date);
    return Optional.empty();
  }

  private String toEodSymbol(String symbol) {
    String normalized = symbol.trim().toUpperCase(Locale.ROOT);
    return normalized.contains(".") ? normalized : normalized + defaultSuffix;
  }

  @SuppressWarnings("unchecked")
  private Optional<BigDecimal> extractClose(Map<?, ?> json) {
    if (json == null) {
      return Optional.empty();
    }
    Object close = ((Map<String, Object>) json).get("close");
    if (close == null) {
      return Optional.empty();
    }
    String raw = String.valueOf(close).trim();
    if (raw.isEmpty() || "NA".equalsIgnoreCase(raw) || "null".equalsIgnoreCase(raw)) {
      return Optional.empty();
    }
    try {
      BigDecimal value = new BigDecimal(raw);
      return value.signum() > 0 ? Optional.of(value) : Optional.empty();
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  private boolean isKnownProviderException(Throwable ex) {
    Throwable unwrapped = Exceptions.unwrap(ex);
    return unwrapped instanceof ExternalProviderRateLimitException
        || unwrapped instanceof ExternalProviderInvalidSymbolException
        || unwrapped instanceof ExternalProviderUpstreamException;
  }
}
