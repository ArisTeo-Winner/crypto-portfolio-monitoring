package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class PortfolioHistoryRateLimiter {

  private final boolean enabled;
  private final int maxRequests;
  private final long windowSeconds;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public PortfolioHistoryRateLimiter(
      @Value("${security.portfolio-history-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.portfolio-history-rate-limit.max-attempts:120}") int maxRequests,
      @Value("${security.portfolio-history-rate-limit.window-seconds:60}") long windowSeconds) {
    this.enabled = enabled;
    this.maxRequests = maxRequests;
    this.windowSeconds = windowSeconds;
  }

  public void validate(HttpServletRequest request) {
    if (!enabled) {
      return;
    }

    String key = request.getRemoteAddr() + ":" + request.getRequestURI();
    long now = Instant.now().getEpochSecond();
    Window window =
        windows.compute(
            key,
            (ignored, existing) -> {
              if (existing == null || now - existing.startedAt() >= windowSeconds) {
                return new Window(now, 1);
              }
              return new Window(existing.startedAt(), existing.count() + 1);
            });

    if (window.count() > maxRequests) {
      throw new IllegalArgumentException("Too many portfolio history requests");
    }
  }

  private record Window(long startedAt, int count) {}
}
