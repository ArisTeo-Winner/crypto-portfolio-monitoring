package com.mx.cryptomonitor.shared.infrastructure.security.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class InMemoryRateLimitStore implements RateLimitStore {

  private final ConcurrentHashMap<String, ClientWindow> windows = new ConcurrentHashMap<>();
  private final LongSupplier clockMillis;

  public InMemoryRateLimitStore(LongSupplier clockMillis) {
    this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
  }

  @Override
  public long consumeFixedWindow(
      String namespace, String clientKey, int maxAttempts, long windowSeconds) {
    long now = clockMillis.getAsLong();
    long windowMillis = windowSeconds * 1000L;
    ClientWindow window =
        windows.computeIfAbsent(composeKey(namespace, clientKey), key -> new ClientWindow());

    synchronized (window) {
      evictExpired(window.timestamps, now - windowMillis);
      if (window.timestamps.size() >= maxAttempts) {
        long oldest = window.timestamps.peekFirst();
        long retryAfterMillis = Math.max(1L, (oldest + windowMillis) - now);
        return secondsUntil(retryAfterMillis);
      }
      window.timestamps.addLast(now);
      cleanupWindow(namespace, clientKey, window, now);
      return 0L;
    }
  }

  @Override
  public long consumeFixedWindowWithBlock(
      String namespace, String clientKey, int maxAttempts, long windowSeconds, long blockSeconds) {
    long now = clockMillis.getAsLong();
    long windowMillis = windowSeconds * 1000L;
    long blockMillis = blockSeconds * 1000L;
    ClientWindow window =
        windows.computeIfAbsent(composeKey(namespace, clientKey), key -> new ClientWindow());

    synchronized (window) {
      if (window.blockedUntilMillis > now) {
        return secondsUntil(window.blockedUntilMillis - now);
      }
      evictExpired(window.timestamps, now - windowMillis);
      if (window.timestamps.size() >= maxAttempts) {
        window.blockedUntilMillis = now + blockMillis;
        window.timestamps.clear();
        cleanupWindow(namespace, clientKey, window, now);
        return secondsUntil(blockMillis);
      }
      window.timestamps.addLast(now);
      cleanupWindow(namespace, clientKey, window, now);
      return 0L;
    }
  }

  private void cleanupWindow(String namespace, String clientKey, ClientWindow window, long now) {
    if (window.timestamps.isEmpty() && window.blockedUntilMillis <= now) {
      windows.remove(composeKey(namespace, clientKey), window);
    }
  }

  private String composeKey(String namespace, String clientKey) {
    return namespace + "::" + clientKey;
  }

  private void evictExpired(Deque<Long> timestamps, long cutoff) {
    while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
      timestamps.removeFirst();
    }
  }

  private long secondsUntil(long millis) {
    return Math.max(1L, (millis + 999L) / 1000L);
  }

  private static final class ClientWindow {
    private final Deque<Long> timestamps = new ArrayDeque<>();
    private long blockedUntilMillis;
  }
}
