package com.mx.cryptomonitor.user.application.port.out;

import java.util.UUID;

import com.mx.cryptomonitor.user.domain.model.AuditEventType;

public interface AuditLogPersistencePort {

  void persist(
      AuditEventType eventType,
      String description,
      UUID userId,
      String ipAddress,
      String userAgent);

  void detachUserFromAuditLogs(UUID userId);
}
