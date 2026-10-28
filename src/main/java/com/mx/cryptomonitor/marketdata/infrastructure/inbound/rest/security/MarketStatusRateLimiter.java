package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.domain.exception.TooManyMarketStatusRequestsException;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.AbstractRequestRateLimiter;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.HttpRequestClientKeyResolver;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.RateLimitStore;

/** Rate limit del endpoint público {@code /api/v1/market/status} por IP de cliente (ADR-0012). */
@Component
public class MarketStatusRateLimiter extends AbstractRequestRateLimiter {

  private static final String NAMESPACE = "market-status";

  @Autowired
  public MarketStatusRateLimiter(
      RateLimitStore rateLimitStore,
      HttpRequestClientKeyResolver clientKeyResolver,
      @Value("${security.market-status-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.market-status-rate-limit.max-attempts:120}") int maxAttempts,
      @Value("${security.market-status-rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${security.market-status-rate-limit.cleanup-interval-seconds:60}")
          long cleanupIntervalSeconds) {
    super(NAMESPACE, enabled, maxAttempts, windowSeconds, null, rateLimitStore, clientKeyResolver);
    validateConfig(maxAttempts, windowSeconds, cleanupIntervalSeconds);
  }

  MarketStatusRateLimiter(
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
    return new TooManyMarketStatusRequestsException(retryAfterSeconds);
  }
}
