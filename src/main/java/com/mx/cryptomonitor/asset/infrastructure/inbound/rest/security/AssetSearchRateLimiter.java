package com.mx.cryptomonitor.asset.infrastructure.inbound.rest.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.asset.domain.exception.TooManyAssetSearchRequestsException;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.AbstractRequestRateLimiter;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.HttpRequestClientKeyResolver;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.RateLimitStore;

@Component
public class AssetSearchRateLimiter extends AbstractRequestRateLimiter {

  private static final String NAMESPACE = "asset-search";

  @Autowired
  public AssetSearchRateLimiter(
      RateLimitStore rateLimitStore,
      HttpRequestClientKeyResolver clientKeyResolver,
      @Value("${security.asset-search-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.asset-search-rate-limit.max-attempts:30}") int maxAttempts,
      @Value("${security.asset-search-rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${security.asset-search-rate-limit.cleanup-interval-seconds:60}")
          long cleanupIntervalSeconds) {
    super(NAMESPACE, enabled, maxAttempts, windowSeconds, null, rateLimitStore, clientKeyResolver);
    validateConfig(maxAttempts, windowSeconds, cleanupIntervalSeconds);
  }

  AssetSearchRateLimiter(
      boolean enabled,
      int maxAttempts,
      long windowSeconds,
      long cleanupIntervalSeconds,
      LongSupplier clockMillis) {
    super(
        NAMESPACE,
        enabled,
        maxAttempts,
        windowSeconds,
        null,
        inMemoryStore(clockMillis),
        localClientKeyResolver());
    validateConfig(maxAttempts, windowSeconds, cleanupIntervalSeconds);
  }

  @Override
  protected RuntimeException createRateLimitException(long retryAfterSeconds) {
    return new TooManyAssetSearchRequestsException(retryAfterSeconds);
  }
}
