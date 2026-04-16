package com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;
import com.mx.cryptomonitor.marketdata.domain.exception.MassiveInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.MassiveRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.MassiveServerException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;

@RequiredArgsConstructor
@Slf4j
public class MassiveAdapter implements StockQuoteProvider {

  private final WebClient webClient;
  private final String massiveBaseUrl;
  private final String massiveApiKey;

  @Override
  public String providerName() {
    return "massive";
  }

  @Override
  public Optional<BigDecimal> getLatest(String symbol) {
    String url =
        String.format(
            "%s/v2/aggs/ticker/%s/prev?adjusted=true&apiKey=%s",
            massiveBaseUrl, symbol, massiveApiKey);

    return webClient
        .get()
        .uri(url)
        .exchangeToMono(response -> response.bodyToMono(Map.class))
        .map(this::extractPreviousClose)
        .onErrorMap(
            ex -> !isKnownProviderException(ex),
            ex -> {
              log.error("Error Massive prev quote", ex);
              return new MassiveServerException(
                  "No se pudo consultar Massive para el simbolo: " + symbol, ex);
            })
        .block();
  }

  @Override
  public Optional<BigDecimal> getHistorical(String symbol, LocalDate date) {
    log.info(
        "Massive historical quote aun no implementado para {} en {}. Se retorna vacio para permitir fallback.",
        symbol,
        date);
    return Optional.empty();
  }

  private Optional<BigDecimal> extractPreviousClose(Map<String, Object> json) {
    Object status = json.get("status");
    String normalizedStatus = status == null ? "" : String.valueOf(status).trim().toUpperCase();

    if (!normalizedStatus.isEmpty() && !"OK".equals(normalizedStatus)) {
      String message = extractMessage(json);
      if (isRateLimitMessage(message)) {
        throw new MassiveRateLimitException(message);
      }
      if (isInvalidSymbolMessage(message)) {
        throw new MassiveInvalidSymbolException(message);
      }
      throw new MassiveServerException(message);
    }

    List<Map<String, Object>> results = (List<Map<String, Object>>) json.get("results");
    if (results == null || results.isEmpty()) {
      return Optional.empty();
    }

    Object close = results.get(0).get("c");
    if (close == null) {
      return Optional.empty();
    }

    return Optional.of(new BigDecimal(String.valueOf(close)));
  }

  private boolean isKnownProviderException(Throwable ex) {
    Throwable unwrapped = Exceptions.unwrap(ex);
    return unwrapped instanceof ExternalProviderRateLimitException
        || unwrapped instanceof ExternalProviderInvalidSymbolException
        || unwrapped instanceof ExternalProviderUpstreamException;
  }

  private String extractMessage(Map<String, Object> json) {
    Object error = json.get("error");
    if (error != null) {
      return String.valueOf(error);
    }
    Object message = json.get("message");
    if (message != null) {
      return String.valueOf(message);
    }
    Object status = json.get("status");
    return status == null ? "Massive retorno una respuesta no exitosa." : String.valueOf(status);
  }

  private boolean isRateLimitMessage(String message) {
    String normalized = message.toLowerCase();
    return normalized.contains("rate limit")
        || normalized.contains("too many requests")
        || normalized.contains("quota")
        || normalized.contains("limit exceeded")
        || normalized.contains("requests per minute")
        || normalized.contains("maximum requests per minute")
        || normalized.contains("please wait or upgrade")
        || normalized.contains("upgrade your subscription");
  }

  private boolean isInvalidSymbolMessage(String message) {
    String normalized = message.toLowerCase();
    return normalized.contains("ticker not found")
        || normalized.contains("symbol not found")
        || normalized.contains("unknown ticker")
        || normalized.contains("invalid ticker")
        || normalized.contains("invalid symbol");
  }
}
