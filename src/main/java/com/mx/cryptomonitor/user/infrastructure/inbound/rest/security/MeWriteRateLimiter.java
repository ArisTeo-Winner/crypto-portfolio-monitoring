package com.mx.cryptomonitor.user.infrastructure.inbound.rest.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.AbstractRequestRateLimiter;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.HttpRequestClientKeyResolver;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.RateLimitStore;
import com.mx.cryptomonitor.user.domain.exception.TooManyMeWriteRequestsException;

@Component
public class MeWriteRateLimiter extends AbstractRequestRateLimiter {

  private static final String NAMESPACE = "me-write";

  @Autowired
  public MeWriteRateLimiter(
      RateLimitStore rateLimitStore,
      HttpRequestClientKeyResolver clientKeyResolver,
      @Value("${security.me-write-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.me-write-rate-limit.max-attempts:20}") int maxAttempts,
      @Value("${security.me-write-rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${security.me-write-rate-limit.cleanup-interval-seconds:300}")
          long cleanupIntervalSeconds) {
    super(NAMESPACE, enabled, maxAttempts, windowSeconds, null, rateLimitStore, clientKeyResolver);
    validateConfig(maxAttempts, windowSeconds, cleanupIntervalSeconds);
  }

  public MeWriteRateLimiter(
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
    return new TooManyMeWriteRequestsException();
  }
}
