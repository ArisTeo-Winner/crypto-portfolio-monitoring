package com.mx.cryptomonitor.asset.infrastructure.outbound.finnhub;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.port.out.AssetProfileProvider;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FinnhubAssetProfileAdapter implements AssetProfileProvider {

  private static final String CACHE_NAME = "asset-logos";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

  private final WebClient webClient;
  private final String apiKey;
  private final CacheManager cacheManager;

  public FinnhubAssetProfileAdapter(
      @Qualifier("finnhubWebClient") WebClient webClient,
      @Value("${finnhub.api-key:}") String apiKey,
      CacheManager cacheManager) {
    this.webClient = webClient;
    this.apiKey = apiKey;
    this.cacheManager = cacheManager;
  }

  @Override
  public Optional<String> getLogoUrl(String symbol) {
    return getProfile(symbol).map(AssetProfile::logoUrl).filter(logoUrl -> !logoUrl.isBlank());
  }

  @Override
  public Optional<AssetProfile> getProfile(String symbol) {
    if (apiKey == null || apiKey.isBlank() || symbol == null || symbol.isBlank()) {
      return Optional.empty();
    }

    String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
    Cache cache = getCache();
    AssetProfile cachedProfile = getCachedProfile(cache, normalizedSymbol);
    if (cachedProfile != null) {
      return Optional.of(cachedProfile);
    }

    try {
      Optional<AssetProfile> profile =
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
              .bodyToMono(FinnhubProfilePayload.class)
              .timeout(REQUEST_TIMEOUT)
              .map(response -> toProfile(normalizedSymbol, response))
              .blockOptional()
              .flatMap(profileResult -> profileResult);

      profile.ifPresent(value -> put(cache, normalizedSymbol, value));
      return profile;
    } catch (RuntimeException ex) {
      log.warn(
          "Finnhub company profile request failed for symbol {}: {}",
          normalizedSymbol,
          ex.getMessage());
      return Optional.empty();
    }
  }

  private Optional<AssetProfile> toProfile(String requestedSymbol, FinnhubProfilePayload response) {
    if (response == null || response.name() == null || response.name().isBlank()) {
      return Optional.empty();
    }

    String responseSymbol =
        response.ticker() == null || response.ticker().isBlank()
            ? requestedSymbol
            : response.ticker().trim().toUpperCase(Locale.ROOT);
    String logoUrl =
        response.logo() == null || response.logo().isBlank() ? null : response.logo().trim();
    return Optional.of(new AssetProfile(responseSymbol, response.name().trim(), logoUrl));
  }

  private void put(Cache cache, String symbol, AssetProfile profile) {
    try {
      if (cache != null) {
        cache.put(symbol, profile);
      }
    } catch (RuntimeException ex) {
      log.warn(
          "Could not cache Finnhub company profile for symbol {}: {}", symbol, ex.getMessage());
    }
  }

  private AssetProfile getCachedProfile(Cache cache, String symbol) {
    try {
      return cache == null ? null : cache.get(symbol, AssetProfile.class);
    } catch (RuntimeException ex) {
      log.warn(
          "Could not read cached Finnhub company profile for symbol {}: {}",
          symbol,
          ex.getMessage());
      return null;
    }
  }

  private Cache getCache() {
    try {
      return cacheManager.getCache(CACHE_NAME);
    } catch (RuntimeException ex) {
      log.warn("Could not access Finnhub company profile cache: {}", ex.getMessage());
      return null;
    }
  }

  private record FinnhubProfilePayload(String logo, String name, String ticker) {}
}
