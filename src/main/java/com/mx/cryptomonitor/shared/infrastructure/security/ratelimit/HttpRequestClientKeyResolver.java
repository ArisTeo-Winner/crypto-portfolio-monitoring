package com.mx.cryptomonitor.shared.infrastructure.security.ratelimit;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class HttpRequestClientKeyResolver {

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
  private static final String UNKNOWN_CLIENT = "unknown";

  public String resolve(HttpServletRequest request) {
    if (request == null) {
      return UNKNOWN_CLIENT;
    }
    String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      String firstIp = forwardedFor.split(",")[0].trim();
      if (!firstIp.isBlank()) {
        return firstIp;
      }
    }
    String remoteAddr = request.getRemoteAddr();
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return UNKNOWN_CLIENT;
    }
    return remoteAddr;
  }
}
