package com.mx.cryptomonitor.shared.infrastructure.security.ratelimit;

public interface RateLimitStore {

  long consumeFixedWindow(String namespace, String clientKey, int maxAttempts, long windowSeconds);

  long consumeFixedWindowWithBlock(
      String namespace, String clientKey, int maxAttempts, long windowSeconds, long blockSeconds);
}
