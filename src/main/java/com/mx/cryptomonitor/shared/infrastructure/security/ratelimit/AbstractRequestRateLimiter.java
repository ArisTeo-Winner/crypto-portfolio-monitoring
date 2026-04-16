package com.mx.cryptomonitor.shared.infrastructure.security.ratelimit;

import java.util.Objects;
import java.util.function.LongSupplier;

import jakarta.servlet.http.HttpServletRequest;

public abstract class AbstractRequestRateLimiter {

  private final String namespace;
  private final boolean enabled;
  private final int maxAttempts;
  private final long windowSeconds;
  private final Long blockSeconds;
  private final RateLimitStore store;
  private final HttpRequestClientKeyResolver clientKeyResolver;

  protected AbstractRequestRateLimiter(
      String namespace,
      boolean enabled,
      int maxAttempts,
      long windowSeconds,
      Long blockSeconds,
      RateLimitStore store,
      HttpRequestClientKeyResolver clientKeyResolver) {
    this.namespace = Objects.requireNonNull(namespace, "namespace");
    this.enabled = enabled;
    this.maxAttempts = maxAttempts;
    this.windowSeconds = windowSeconds;
    this.blockSeconds = blockSeconds;
    this.store = Objects.requireNonNull(store, "store");
    this.clientKeyResolver = Objects.requireNonNull(clientKeyResolver, "clientKeyResolver");
  }

  protected static void validateConfig(
      int maxAttempts, long windowSeconds, long... additionalSeconds) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("Rate limiter configuration values must be positive.");
    }
    if (windowSeconds < 1) {
      throw new IllegalArgumentException("Rate limiter configuration values must be positive.");
    }
    for (long value : additionalSeconds) {
      if (value < 1) {
        throw new IllegalArgumentException("Rate limiter configuration values must be positive.");
      }
    }
  }

  protected static RateLimitStore inMemoryStore(LongSupplier clockMillis) {
    return new InMemoryRateLimitStore(clockMillis);
  }

  protected static HttpRequestClientKeyResolver localClientKeyResolver() {
    return new HttpRequestClientKeyResolver();
  }

  public void validateOrThrow(HttpServletRequest request) {
    if (!enabled) {
      return;
    }

    String clientKey = clientKeyResolver.resolve(request);
    long retryAfterSeconds =
        blockSeconds == null
            ? store.consumeFixedWindow(namespace, clientKey, maxAttempts, windowSeconds)
            : store.consumeFixedWindowWithBlock(
                namespace, clientKey, maxAttempts, windowSeconds, blockSeconds);

    if (retryAfterSeconds > 0L) {
      throw createRateLimitException(retryAfterSeconds);
    }
  }

  protected abstract RuntimeException createRateLimitException(long retryAfterSeconds);
}
