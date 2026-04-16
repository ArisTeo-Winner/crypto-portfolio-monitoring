package com.mx.cryptomonitor.user.infrastructure.inbound.rest.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.AbstractRequestRateLimiter;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.HttpRequestClientKeyResolver;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.RateLimitStore;
import com.mx.cryptomonitor.user.domain.exception.TooManyMeDeleteRequestsException;

@Component
public class MeDeleteRateLimiter extends AbstractRequestRateLimiter {

  private static final String NAMESPACE = "me-delete";

  @Autowired
  public MeDeleteRateLimiter(
      RateLimitStore rateLimitStore,
      HttpRequestClientKeyResolver clientKeyResolver,
      @Value("${security.me-delete-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.me-delete-rate-limit.max-attempts:2}") int maxAttempts,
      @Value("${security.me-delete-rate-limit.window-seconds:3600}") long windowSeconds,
      @Value("${security.me-delete-rate-limit.cleanup-interval-seconds:900}")
          long cleanupIntervalSeconds) {
    super(NAMESPACE, enabled, maxAttempts, windowSeconds, null, rateLimitStore, clientKeyResolver);
    validateConfig(maxAttempts, windowSeconds, cleanupIntervalSeconds);
  }

  public MeDeleteRateLimiter(
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
    return new TooManyMeDeleteRequestsException(retryAfterSeconds);
  }
}
