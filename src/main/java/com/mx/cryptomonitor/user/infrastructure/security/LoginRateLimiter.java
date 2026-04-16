package com.mx.cryptomonitor.user.infrastructure.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.AbstractRequestRateLimiter;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.HttpRequestClientKeyResolver;
import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.RateLimitStore;
import com.mx.cryptomonitor.user.domain.exception.TooManyLoginRequestsException;

@Component
public class LoginRateLimiter extends AbstractRequestRateLimiter {

  private static final String NAMESPACE = "login";

  @Autowired
  public LoginRateLimiter(
      RateLimitStore rateLimitStore,
      HttpRequestClientKeyResolver clientKeyResolver,
      @Value("${security.login-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.login-rate-limit.max-attempts:10}") int maxAttempts,
      @Value("${security.login-rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${security.login-rate-limit.block-seconds:300}") long blockSeconds,
      @Value("${security.login-rate-limit.cleanup-interval-seconds:300}")
          long cleanupIntervalSeconds) {
    super(
        NAMESPACE,
        enabled,
        maxAttempts,
        windowSeconds,
        blockSeconds,
        rateLimitStore,
        clientKeyResolver);
    validateConfig(maxAttempts, windowSeconds, blockSeconds, cleanupIntervalSeconds);
  }

  public LoginRateLimiter(
      boolean enabled,
      int maxAttempts,
      long windowSeconds,
      long blockSeconds,
      long cleanupIntervalSeconds,
      LongSupplier clockMillis) {
    super(
        NAMESPACE,
        enabled,
        maxAttempts,
        windowSeconds,
        blockSeconds,
        inMemoryStore(clockMillis),
        localClientKeyResolver());
    validateConfig(maxAttempts, windowSeconds, blockSeconds, cleanupIntervalSeconds);
  }

  @Override
  protected RuntimeException createRateLimitException(long retryAfterSeconds) {
    return new TooManyLoginRequestsException(retryAfterSeconds);
  }
}
