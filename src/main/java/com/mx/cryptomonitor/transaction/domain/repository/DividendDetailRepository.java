package com.mx.cryptomonitor.transaction.domain.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;

public interface DividendDetailRepository extends JpaRepository<DividendDetail, UUID> {
  Optional<DividendDetail> findByTransactionTransactionId(UUID transactionId);

  /** Idempotencia del accrual (ADR-0014 F3c): un dividendo por usuario/simbolo/ex-date. */
  boolean existsByTransaction_User_IdAndTransaction_AssetSymbolAndExDividendDate(
      UUID userId, String assetSymbol, LocalDate exDividendDate);
}
