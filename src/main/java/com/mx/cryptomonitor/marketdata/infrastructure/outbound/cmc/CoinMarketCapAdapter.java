package com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapInvalidParamException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapServerException;
import com.mx.cryptomonitor.marketdata.domain.model.AssetType;
import com.mx.cryptomonitor.marketdata.domain.model.Money;
import com.mx.cryptomonitor.marketdata.domain.model.PriceQuote;
import com.mx.cryptomonitor.marketdata.domain.model.ProviderId;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.CoinMarketCapProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.dto.CmcCrypto;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.dto.CmcQuotesLatestResponse;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class CoinMarketCapAdapter implements AssetPricePort {

  private final WebClient webClient;
  private final CoinMarketCapProperties props;
  private final MeterRegistry meterRegistry;

  public CoinMarketCapAdapter(
      @Qualifier("coinMarketCapWebClient") WebClient webClient,
      CoinMarketCapProperties props,
      MeterRegistry meterRegistry) {
    this.webClient = webClient;
    this.props = props;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<PriceQuote> getCryptoPrice(String symbol) {
    if (!props.enabled()) {
      return Mono.error(new CoinMarketCapServerException("CoinMarketCap integration is disabled"));
    }

    String normalizedSymbol = normalizeSymbol(symbol);

    return Mono.defer(
        () -> {
          io.micrometer.core.instrument.Timer.Sample sample =
              io.micrometer.core.instrument.Timer.start(meterRegistry);

          return webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/v2/cryptocurrency/quotes/latest")
                          .queryParam("symbol", normalizedSymbol)
                          .build())
              .retrieve()
              .onStatus(
                  HttpStatusCode::is4xxClientError,
                  response -> {
                    if (response.statusCode().value() == 429) {
                      return Mono.error(new CoinMarketCapRateLimitException("Rate limit exceeded"));
                    }
                    if (response.statusCode().value() == 400) {
                      return response
                          .bodyToMono(String.class)
                          .defaultIfEmpty("Invalid parameters")
                          .flatMap(
                              body ->
                                  Mono.error(
                                      new CoinMarketCapInvalidParamException(
                                          "Invalid parameters: " + body)));
                    }
                    return response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Client error")
                        .flatMap(
                            body ->
                                Mono.error(
                                    new CoinMarketCapInvalidParamException(
                                        "Client Error " + response.statusCode() + ": " + body)));
                  })
              .onStatus(
                  HttpStatusCode::is5xxServerError,
                  response ->
                      response
                          .bodyToMono(String.class)
                          .defaultIfEmpty("Server error")
                          .flatMap(
                              body ->
                                  Mono.error(
                                      new CoinMarketCapServerException(
                                          "Server Error " + response.statusCode() + ": " + body))))
              .bodyToMono(CmcQuotesLatestResponse.class)
              .timeout(
                  props.responseTimeout() != null ? props.responseTimeout() : Duration.ofSeconds(5))
              .retryWhen(
                  Retry.backoff(3, Duration.ofMillis(500))
                      .filter(
                          throwable ->
                              throwable instanceof CoinMarketCapServerException
                                  || throwable instanceof java.util.concurrent.TimeoutException)
                      .onRetryExhaustedThrow(
                          (retryBackoffSpec, retrySignal) ->
                              new CoinMarketCapServerException(
                                  "External service failed after max retries",
                                  retrySignal.failure())))
              .flatMap(response -> extractPrice(response, normalizedSymbol))
              .doOnSuccess(
                  pq -> {
                    sample.stop(
                        meterRegistry.timer("coinmarketcap.api.latency", "outcome", "success"));
                    meterRegistry
                        .counter("coinmarketcap.api.requests", "outcome", "success")
                        .increment();
                  })
              .doOnError(
                  ex -> {
                    String errorType = ex.getClass().getSimpleName();
                    sample.stop(
                        meterRegistry.timer("coinmarketcap.api.latency", "outcome", "failure"));
                    meterRegistry
                        .counter(
                            "coinmarketcap.api.requests", "outcome", "failure", "error", errorType)
                        .increment();
                  });
        });
  }

  @Override
  public Mono<BigDecimal> getCryptoPriceAmount(String symbol) {
    return getCryptoPrice(symbol).map(priceQuote -> priceQuote.price().amount());
  }

  private Mono<PriceQuote> extractPrice(CmcQuotesLatestResponse response, String symbol) {
    if (response.status() != null && response.status().error_code() != 0) {
      String msg = "CMC API Error: " + response.status().error_message();
      return Mono.error(new CoinMarketCapServerException(msg));
    }

    Map<String, List<CmcCrypto>> data = response.data();
    if (data == null || !data.containsKey(symbol)) {
      return Mono.error(new CoinMarketCapInvalidParamException("Symbol not found: " + symbol));
    }

    List<CmcCrypto> cryptoList = data.get(symbol);
    if (cryptoList == null || cryptoList.isEmpty()) {
      return Mono.error(new CoinMarketCapInvalidParamException("No data for symbol: " + symbol));
    }

    CmcCrypto cryptoData = cryptoList.get(0);
    if (cryptoData.quote() == null || !cryptoData.quote().containsKey("USD")) {
      return Mono.error(new CoinMarketCapServerException("No USD quote available for " + symbol));
    }

    BigDecimal price = cryptoData.quote().get("USD").price();
    return Mono.just(
        new PriceQuote(
            symbol,
            AssetType.CRYPTO,
            new Money(price, "USD"),
            Instant.now(),
            ProviderId.COINMARKETCAP));
  }

  private String normalizeSymbol(String symbol) {
    if (symbol == null || symbol.trim().isEmpty()) {
      throw new CoinMarketCapInvalidParamException("Symbol cannot be empty");
    }
    return symbol.trim().toUpperCase();
  }
}
