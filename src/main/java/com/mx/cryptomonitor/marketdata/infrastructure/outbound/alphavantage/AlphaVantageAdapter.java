package com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.domain.exception.AlphaVantageInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.AlphaVantageRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.AlphaVantageServerException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;

@RequiredArgsConstructor
@Slf4j
public class AlphaVantageAdapter implements StockQuoteProvider {

  private static final String NOTE_KEY = "Note";
  private static final String INFORMATION_KEY = "Information";
  private static final String ERROR_MESSAGE_KEY = "Error Message";
  private static final String GLOBAL_QUOTE_KEY = "Global Quote";
  private static final String TIME_SERIES_DAILY_KEY = "Time Series (Daily)";

  private final WebClient webClient;
  private final String alphaVantageBaseUrl;
  private final String alphaVantageApiKey;

  @Override
  public String providerName() {
    return "alphavantage";
  }

  @Override
  public Optional<BigDecimal> getLatest(String symbol) {
    String url =
        String.format(
            "%s/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
            alphaVantageBaseUrl, symbol, alphaVantageApiKey);

    return webClient
        .get()
        .uri(url)
        .retrieve()
        .bodyToMono(Map.class)
        .map(this::extractGlobalQuote)
        .onErrorMap(
            ex -> !isKnownProviderException(ex),
            ex -> {
              log.error("Error AlphaVantage GLOBAL_QUOTE", ex);
              return new AlphaVantageServerException(
                  "No se pudo consultar AlphaVantage para el simbolo: " + symbol, ex);
            })
        .block();
  }

  private Optional<BigDecimal> extractGlobalQuote(Map<String, Object> json) {
    throwIfProviderReportedError(json);
    if (!json.containsKey(GLOBAL_QUOTE_KEY)) {
      return Optional.empty();
    }

    Map<String, String> quote = (Map) json.get(GLOBAL_QUOTE_KEY);
    String priceStr = quote.get("05. price");

    return Optional.ofNullable(priceStr).map(BigDecimal::new);
  }

  @Override
  public Optional<BigDecimal> getHistorical(String symbol, LocalDate date) {
    log.info("Consultando precio historico en AlphaVantage para {} en {}", symbol, date);

    return webClient
        .get()
        .uri(
            String.format(
                "%s/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                alphaVantageBaseUrl, symbol, alphaVantageApiKey))
        .retrieve()
        .bodyToMono(Map.class)
        .map(json -> extractHistoricalQuote(json, date))
        .onErrorMap(
            ex -> !isKnownProviderException(ex),
            ex -> {
              log.error("Error AlphaVantage TIME_SERIES_DAILY", ex);
              return new AlphaVantageServerException(
                  "No se pudo consultar AlphaVantage historico para el simbolo: " + symbol, ex);
            })
        .block();
  }

  private boolean isKnownProviderException(Throwable ex) {
    Throwable unwrapped = Exceptions.unwrap(ex);
    return unwrapped instanceof ExternalProviderRateLimitException
        || unwrapped instanceof ExternalProviderInvalidSymbolException
        || unwrapped instanceof ExternalProviderUpstreamException;
  }

  private Optional<BigDecimal> extractHistoricalQuote(Map<String, Object> json, LocalDate date) {
    throwIfProviderReportedError(json);
    if (!json.containsKey(TIME_SERIES_DAILY_KEY)) {
      return Optional.empty();
    }

    Map<String, Map<String, String>> timeSeries =
        (Map<String, Map<String, String>>) json.get(TIME_SERIES_DAILY_KEY);
    Map<String, String> dailyData = timeSeries.get(date.toString());
    if (dailyData == null) {
      return Optional.empty();
    }

    String closePrice = dailyData.get("4. close");
    return Optional.ofNullable(closePrice).map(BigDecimal::new);
  }

  private void throwIfProviderReportedError(Map<String, Object> json) {
    if (json.containsKey(NOTE_KEY)) {
      throw new AlphaVantageRateLimitException(String.valueOf(json.get(NOTE_KEY)));
    }
    if (json.containsKey(INFORMATION_KEY)) {
      throw new AlphaVantageServerException(String.valueOf(json.get(INFORMATION_KEY)));
    }
    if (json.containsKey(ERROR_MESSAGE_KEY)) {
      throw new AlphaVantageInvalidSymbolException(String.valueOf(json.get(ERROR_MESSAGE_KEY)));
    }
  }
}
