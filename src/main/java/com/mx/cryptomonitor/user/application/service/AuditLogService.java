package com.mx.cryptomonitor.user.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.mx.cryptomonitor.user.application.port.out.AuditLogPersistencePort;
import com.mx.cryptomonitor.user.domain.model.AuditEventType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogService {

  private static final int MAX_DESCRIPTION_LENGTH = 2048;
  private static final int MAX_USER_AGENT_LENGTH = 2048;
  private static final int MAX_IP_LENGTH = 64;

  private final AuditLogPersistencePort auditLogPersistencePort;

  public void log(AuditEventType eventType, String description, UUID userId) {
    HttpServletRequest request = resolveCurrentRequest();
    log(eventType, description, userId, request);
  }

  public void log(
      AuditEventType eventType, String description, UUID userId, HttpServletRequest request) {
    String ipAddress = extractClientIp(request);
    String userAgent = request != null ? request.getHeader("User-Agent") : null;
    auditLogPersistencePort.persist(
        eventType,
        truncate(safeDefault(description, "No description"), MAX_DESCRIPTION_LENGTH),
        userId,
        truncate(ipAddress, MAX_IP_LENGTH),
        truncate(userAgent, MAX_USER_AGENT_LENGTH));
  }

  public void detachUserFromAuditLogs(UUID userId) {
    auditLogPersistencePort.detachUserFromAuditLogs(userId);
  }

  private HttpServletRequest resolveCurrentRequest() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      return attributes.getRequest();
    }
    return null;
  }

  private String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return null;
    }

    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      String firstHop = xForwardedFor.split(",")[0].trim();
      if (!firstHop.isBlank()) {
        return firstHop;
      }
    }

    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isBlank()) {
      return xRealIp.trim();
    }

    return request.getRemoteAddr();
  }

  private String safeDefault(String value, String defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value.trim();
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
