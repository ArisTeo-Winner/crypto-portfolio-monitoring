package com.mx.cryptomonitor.transaction.infrastructure.outbound.audit;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.transaction.application.port.out.TransactionAuditPort;
import com.mx.cryptomonitor.user.application.service.AuditLogService;
import com.mx.cryptomonitor.user.domain.model.AuditEventType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransactionAuditAdapter implements TransactionAuditPort {

  private final AuditLogService auditLogService;

  @Override
  public void logCreateSuccess(UUID userId, String description) {
    auditLogService.log(AuditEventType.TRANSACTION_CREATE_SUCCESS, description, userId);
  }

  @Override
  public void logCreateFailure(UUID userId, String description) {
    auditLogService.log(AuditEventType.TRANSACTION_CREATE_FAILED, description, userId);
  }

  @Override
  public void logUpdateSuccess(UUID userId, String description) {
    auditLogService.log(AuditEventType.TRANSACTION_UPDATE_SUCCESS, description, userId);
  }

  @Override
  public void logUpdateFailure(UUID userId, String description) {
    auditLogService.log(AuditEventType.TRANSACTION_UPDATE_FAILED, description, userId);
  }

  @Override
  public void logDeleteSuccess(UUID userId, String description) {
    auditLogService.log(AuditEventType.TRANSACTION_DELETE_SUCCESS, description, userId);
  }

  @Override
  public void logDeleteFailure(UUID userId, String description) {
    auditLogService.log(AuditEventType.TRANSACTION_DELETE_FAILED, description, userId);
  }
}
