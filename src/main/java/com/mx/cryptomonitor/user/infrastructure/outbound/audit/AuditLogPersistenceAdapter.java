package com.mx.cryptomonitor.user.infrastructure.outbound.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.user.application.port.out.AuditLogPersistencePort;
import com.mx.cryptomonitor.user.domain.model.AuditEventType;
import com.mx.cryptomonitor.user.domain.model.AuditLog;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.AuditLogRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class AuditLogPersistenceAdapter implements AuditLogPersistencePort {

  private static final Logger logger = LoggerFactory.getLogger(AuditLogPersistenceAdapter.class);

  private final AuditLogRepository auditLogRepository;
  private final EntityManager entityManager;
  private final UserRepository userRepository;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persist(
      AuditEventType eventType,
      String description,
      UUID userId,
      String ipAddress,
      String userAgent) {
    try {
      AuditLog entry = new AuditLog();
      entry.setEventType(eventType);
      entry.setDescription(description);
      entry.setIpAddress(ipAddress);
      entry.setUserAgent(userAgent);
      entry.setEventTimestamp(OffsetDateTime.now());

      if (userId != null && userRepository.existsById(userId)) {
        entry.setUser(entityManager.getReference(User.class, userId));
      }

      auditLogRepository.save(entry);
    } catch (Exception ex) {
      logger.warn("Audit log write failed for eventType={}", eventType, ex);
    }
  }

  @Override
  @Transactional
  public void detachUserFromAuditLogs(UUID userId) {
    if (userId == null) {
      return;
    }
    try {
      auditLogRepository.detachUserFromLogs(userId);
    } catch (Exception ex) {
      logger.warn("Audit log detach failed for userId={}", userId, ex);
    }
  }
}
