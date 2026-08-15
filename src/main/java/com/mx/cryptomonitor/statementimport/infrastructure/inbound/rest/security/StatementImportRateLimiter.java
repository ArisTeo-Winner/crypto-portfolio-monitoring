package com.mx.cryptomonitor.statementimport.infrastructure.inbound.rest.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Acota cargas de PDF por usuario/IP para que un solo usuario no sature la cola compartida de
 * StatementImportJobWorker (relevante en B2C: el costo de procesar es compartido entre todos los
 * usuarios, no aislado por cliente como en un contrato B2B).
 */
@Component
public class StatementImportRateLimiter {

  private final boolean enabled;
  private final int maxRequests;
  private final long windowSeconds;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public StatementImportRateLimiter(
      @Value("${security.statement-import-rate-limit.enabled:true}") boolean enabled,
      @Value("${security.statement-import-rate-limit.max-attempts:20}") int maxRequests,
      @Value("${security.statement-import-rate-limit.window-seconds:3600}") long windowSeconds) {
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
      throw new IllegalArgumentException("Demasiadas cargas de estados de cuenta");
    }
  }

  private record Window(long startedAt, int count) {}
}
