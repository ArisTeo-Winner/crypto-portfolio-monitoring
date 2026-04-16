package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.domain.exception.TooManyCryptoPriceRequestsException;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.AbstractRequestRateLimiter;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.HttpRequestClientKeyResolver;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.RateLimitStore;

@Component
public class CryptoPriceRateLimiter extends AbstractRequestRateLimiter {

  private static final String NAMESPACE = "crypto-price";

  @Autowired
  public CryptoPriceRateLimiter(
      RateLimitStore rateLimitStore,
      HttpRequestClientKeyResolver clientKeyResolver,
      @Value("${security.crypto-price-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.crypto-price-rate-limit.max-attempts:60}") int maxAttempts,
      @Value("${security.crypto-price-rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${security.crypto-price-rate-limit.cleanup-interval-seconds:60}")
          long cleanupIntervalSeconds) {
    super(NAMESPACE, enabled, maxAttempts, windowSeconds, null, rateLimitStore, clientKeyResolver);
    validateConfig(maxAttempts, windowSeconds, cleanupIntervalSeconds);
  }

  CryptoPriceRateLimiter(
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
    return new TooManyCryptoPriceRequestsException(retryAfterSeconds);
  }
}
