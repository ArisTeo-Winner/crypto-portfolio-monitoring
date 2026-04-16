package com.mx.cryptomonitor.user.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.cryptomonitor.user.domain.model.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update AuditLog a set a.user = null where a.user.id = :userId")
  int detachUserFromLogs(@Param("userId") UUID userId);
}
