package com.mx.cryptomonitor.asset.infrastructure.outbound.coingecko;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mx.cryptomonitor.asset.application.port.out.CryptoLogoPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve logos de CRYPTO desde CoinGecko {@code GET /coins/markets?vs_currency=usd&symbols=...}.
 *
 * <p>Una sola llamada batch por sync (todos los símbolos del catálogo). Ante colisión de símbolo
 * (varias monedas con el mismo ticker) se conserva la de mayor {@code market_cap}. Cualquier fallo
 * devuelve un mapa vacío para que el llamador aplique su fallback (jsDelivr → placeholder).
 */
@Component
@Slf4j
public class CoinGeckoCryptoLogoAdapter implements CryptoLogoPort {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final WebClient webClient;

  public CoinGeckoCryptoLogoAdapter(@Qualifier("coinGeckoLogoWebClient") WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Map<String, String> fetchLogosBySymbol(Collection<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      return Map.of();
    }
    String csv =
        symbols.stream()
            .filter(s -> s != null && !s.isBlank())
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .collect(Collectors.joining(","));
    if (csv.isBlank()) {
      return Map.of();
    }

    try {
      List<CoinMarket> coins =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/coins/markets")
                          .queryParam("vs_currency", "usd")
                          .queryParam("symbols", csv)
                          .queryParam("per_page", 250)
                          .queryParam("page", 1)
                          .build())
              .retrieve()
              .bodyToFlux(CoinMarket.class)
              .timeout(TIMEOUT)
              .collectList()
              .block();

      if (coins == null || coins.isEmpty()) {
        return Map.of();
      }

      Map<String, String> logos = new HashMap<>();
      Map<String, Long> bestMarketCap = new HashMap<>();
      for (CoinMarket coin : coins) {
        if (coin.symbol() == null || coin.image() == null || coin.image().isBlank()) {
          continue;
        }
        String symbol = coin.symbol().trim().toUpperCase(Locale.ROOT);
        long marketCap = coin.marketCap() == null ? 0L : coin.marketCap();
        if (!logos.containsKey(symbol) || marketCap > bestMarketCap.getOrDefault(symbol, -1L)) {
          logos.put(symbol, coin.image());
          bestMarketCap.put(symbol, marketCap);
        }
      }
      return logos;
    } catch (RuntimeException ex) {
      log.warn("CoinGecko logo fetch failed for symbols [{}]: {}", csv, ex.getMessage());
      return Map.of();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CoinMarket(
      String symbol, String image, @JsonProperty("market_cap") Long marketCap) {}
}
