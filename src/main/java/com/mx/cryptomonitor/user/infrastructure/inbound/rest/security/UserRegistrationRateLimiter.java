package com.mx.cryptomonitor.user.infrastructure.inbound.rest.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.AbstractRequestRateLimiter;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.HttpRequestClientKeyResolver;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.RateLimitStore;
import com.mx.cryptomonitor.user.domain.exception.TooManyRegistrationRequestsException;

@Component
public class UserRegistrationRateLimiter extends AbstractRequestRateLimiter {

  private static final String NAMESPACE = "registration";

  @Autowired
  public UserRegistrationRateLimiter(
      RateLimitStore rateLimitStore,
      HttpRequestClientKeyResolver clientKeyResolver,
      @Value("${security.registration-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.registration-rate-limit.max-attempts:5}") int maxAttempts,
      @Value("${security.registration-rate-limit.window-seconds:600}") long windowSeconds,
      @Value("${security.registration-rate-limit.cleanup-interval-seconds:300}")
          long cleanupIntervalSeconds) {
    super(NAMESPACE, enabled, maxAttempts, windowSeconds, null, rateLimitStore, clientKeyResolver);
    validateConfig(maxAttempts, windowSeconds, cleanupIntervalSeconds);
  }

  public UserRegistrationRateLimiter(
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

  public UserRegistrationRateLimiter(
      boolean enabled, int maxAttempts, long windowSeconds, long cleanupIntervalSeconds) {
    this(enabled, maxAttempts, windowSeconds, cleanupIntervalSeconds, System::currentTimeMillis);
  }

  @Override
  protected RuntimeException createRateLimitException(long retryAfterSeconds) {
    return new TooManyRegistrationRequestsException(retryAfterSeconds);
  }
}
