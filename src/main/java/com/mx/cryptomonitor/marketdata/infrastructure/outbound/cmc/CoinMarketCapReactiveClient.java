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

import com.mx.cryptomonitor.marketdata.domain.model.AssetType;
import com.mx.cryptomonitor.marketdata.domain.model.Money;
import com.mx.cryptomonitor.marketdata.domain.model.PriceQuote;
import com.mx.cryptomonitor.marketdata.domain.model.ProviderId;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.CoinMarketCapProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.dto.CmcCrypto;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.dto.CmcQuotesLatestResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CoinMarketCapReactiveClient {

  private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(5);
  private static final String USD = "USD";

  private final WebClient webClient;
  private final CoinMarketCapProperties props;

  public CoinMarketCapReactiveClient(
      @Qualifier("coinMarketCapWebClient") WebClient webClient, CoinMarketCapProperties props) {
    this.webClient = webClient;
    this.props = props;
  }

  public Mono<CmcQuotesLatestResponse> getLatestQuotesRaw(String symbol) {
    String normalizedSymbol = normalizeSymbol(symbol);
    log.debug("Invocando CoinMarketCap RAW latest quotes para symbol={}", normalizedSymbol);

    if (!props.enabled()) {
      log.warn("CoinMarketCap is disabled");
      return Mono.error(
          new CoinMarketCapClientException(
              "CoinMarketCap is disabled", CmcErrorType.SERVER_ERROR, 503, null, null, null));
    }

    return webClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/v2/cryptocurrency/quotes/latest")
                    .queryParam("symbol", normalizedSymbol)
                    .build())
        .retrieve()
        .onStatus(HttpStatusCode::isError, CmcErrorDecoder::decode)
        .bodyToMono(CmcQuotesLatestResponse.class)
        .timeout(resolveTimeout());
  }

  public Mono<PriceQuote> getLatestCryptoUsdQuote(String symbol) {
    log.debug("Invocando CoinMarketCap latest quotes para symbol={}", symbol);

    if (!props.enabled()) {
      log.warn("CoinMarketCap is disabled");
      return Mono.error(
          new CoinMarketCapClientException(
              "CoinMarketCap is disabled", CmcErrorType.SERVER_ERROR, 503, null, null, null));
    }

    String normalizedSymbol = normalizeSymbol(symbol);
    String convert = props.defaultConvert() == null ? USD : props.defaultConvert().toUpperCase();
    if (!USD.equals(convert)) {
      return Mono.error(
          new CoinMarketCapClientException(
              "Only convert=USD is supported",
              CmcErrorType.BAD_REQUEST,
              400,
              null,
              "Unsupported conversion currency",
              convert));
    }

    return getLatestQuotesRaw(normalizedSymbol)
        .flatMap(response -> extractUsdPrice(response, normalizedSymbol))
        .map(
            price ->
                new PriceQuote(
                    normalizedSymbol,
                    AssetType.CRYPTO,
                    new Money(price, USD),
                    Instant.now(),
                    ProviderId.COINMARKETCAP));
  }

  public Mono<PriceQuote> getLatestCrytoUsdQuote(String symbol) {
    return getLatestCryptoUsdQuote(symbol);
  }

  private String normalizeSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new CoinMarketCapClientException(
          "Symbol cannot be empty", CmcErrorType.BAD_REQUEST, 400, null, null, symbol);
    }
    return symbol.trim().toUpperCase();
  }

  private Duration resolveTimeout() {
    return props.responseTimeout() != null ? props.responseTimeout() : DEFAULT_RESPONSE_TIMEOUT;
  }

  private Mono<BigDecimal> extractUsdPrice(CmcQuotesLatestResponse response, String symbol) {
    if (response == null) {
      return Mono.error(
          new CoinMarketCapClientException(
              "Empty response from CoinMarketCap",
              CmcErrorType.SERVER_ERROR,
              502,
              null,
              null,
              null));
    }

    if (response.status() != null
        && response.status().error_code() != null
        && response.status().error_code() != 0) {
      return Mono.error(
          new CoinMarketCapClientException(
              "CoinMarketCap returned application error",
              CmcErrorType.SERVER_ERROR,
              200,
              response.status().error_code(),
              response.status().error_message(),
              null));
    }

    Map<String, List<CmcCrypto>> data = response.data();
    if (data == null || !data.containsKey(symbol) || data.get(symbol) == null) {
      return Mono.error(
          new CoinMarketCapClientException(
              "No data for symbol " + symbol, CmcErrorType.DATA_NOT_FOUND, 200, 0, null, null));
    }

    List<CmcCrypto> cryptos = data.get(symbol);
    if (cryptos.isEmpty()
        || cryptos.get(0).quote() == null
        || !cryptos.get(0).quote().containsKey(USD)) {
      return Mono.error(
          new CoinMarketCapClientException(
              "No USD quote for symbol " + symbol,
              CmcErrorType.DATA_NOT_FOUND,
              200,
              0,
              null,
              null));
    }

    BigDecimal price = cryptos.get(0).quote().get(USD).price();
    if (price == null) {
      return Mono.error(
          new CoinMarketCapClientException(
              "USD quote without price for symbol " + symbol,
              CmcErrorType.DATA_NOT_FOUND,
              200,
              0,
              null,
              null));
    }
    return Mono.just(price);
  }
}
