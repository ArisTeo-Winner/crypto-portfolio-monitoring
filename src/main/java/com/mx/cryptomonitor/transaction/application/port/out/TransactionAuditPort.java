package com.mx.cryptomonitor.transaction.application.port.out;

import java.util.UUID;

public interface TransactionAuditPort {

  void logCreateSuccess(UUID userId, String description);

  void logCreateFailure(UUID userId, String description);

  void logUpdateSuccess(UUID userId, String description);

  void logUpdateFailure(UUID userId, String description);

  void logDeleteSuccess(UUID userId, String description);

  void logDeleteFailure(UUID userId, String description);
}
