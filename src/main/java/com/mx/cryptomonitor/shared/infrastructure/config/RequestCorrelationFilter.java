package com.mx.cryptomonitor.shared.infrastructure.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {

  private static final Logger ACCESS_LOG = LoggerFactory.getLogger("http.access");
  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String TRACE_ID_KEY = "traceId";
  private static final String SPAN_ID_KEY = "spanId";
  private static final String REQUEST_ID_KEY = "requestId";
  private static final String CLIENT_IP_KEY = "clientIp";
  private static final String HTTP_METHOD_KEY = "httpMethod";
  private static final String HTTP_PATH_KEY = "httpPath";
  private static final String HTTP_STATUS_KEY = "httpStatus";
  private static final String DURATION_MS_KEY = "durationMs";
  private static final String PRINCIPAL_KEY = "principal";
  private static final int MAX_REQUEST_ID_LENGTH = 120;
  private final ObjectProvider<Tracer> tracerProvider;

  public RequestCorrelationFilter(ObjectProvider<Tracer> tracerProvider) {
    this.tracerProvider = tracerProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
    long startNanos = System.nanoTime();
    response.setHeader(REQUEST_ID_HEADER, requestId);

    MDC.put(REQUEST_ID_KEY, requestId);
    MDC.put(CLIENT_IP_KEY, resolveClientIp(request));
    MDC.put(HTTP_METHOD_KEY, request.getMethod());
    MDC.put(HTTP_PATH_KEY, request.getRequestURI());
    try {
      filterChain.doFilter(request, response);
    } finally {
      putTraceContext();
      MDC.put(HTTP_STATUS_KEY, String.valueOf(response.getStatus()));
      MDC.put(DURATION_MS_KEY, String.valueOf((System.nanoTime() - startNanos) / 1_000_000L));
      putPrincipal();
      if (shouldLogAccess(request)) {
        ACCESS_LOG.info("http_request_completed");
      }
      clearMdc();
    }
  }

  private String resolveRequestId(String requestId) {
    if (requestId != null) {
      String normalized = requestId.trim();
      if (!normalized.isEmpty() && normalized.length() <= MAX_REQUEST_ID_LENGTH) {
        return normalized;
      }
    }
    return UUID.randomUUID().toString();
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null ? remoteAddr : "unknown";
  }

  private void putPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return;
    }
    if (authentication instanceof AnonymousAuthenticationToken) {
      return;
    }
    String principal = authentication.getName();
    if (principal != null && !principal.isBlank()) {
      MDC.put(PRINCIPAL_KEY, principal);
    }
  }

  private void putTraceContext() {
    Tracer tracer = tracerProvider.getIfAvailable();
    if (tracer == null) {
      return;
    }
    Span currentSpan = tracer.currentSpan();
    if (currentSpan == null) {
      return;
    }
    MDC.put(TRACE_ID_KEY, currentSpan.context().traceId());
    MDC.put(SPAN_ID_KEY, currentSpan.context().spanId());
  }

  private boolean shouldLogAccess(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path != null
        && !path.startsWith("/actuator/health")
        && !"/actuator/prometheus".equals(path);
  }

  private void clearMdc() {
    MDC.remove(PRINCIPAL_KEY);
    MDC.remove(DURATION_MS_KEY);
    MDC.remove(HTTP_STATUS_KEY);
    MDC.remove(HTTP_PATH_KEY);
    MDC.remove(HTTP_METHOD_KEY);
    MDC.remove(CLIENT_IP_KEY);
    MDC.remove(REQUEST_ID_KEY);
    MDC.remove(SPAN_ID_KEY);
    MDC.remove(TRACE_ID_KEY);
  }
}
