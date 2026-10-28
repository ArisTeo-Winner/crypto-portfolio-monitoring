package com.mx.cryptomonitor.transaction.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;

public interface DividendDetailRepository extends JpaRepository<DividendDetail, UUID> {
  Optional<DividendDetail> findByTransactionTransactionId(UUID transactionId);

  /** Idempotencia del accrual (ADR-0014 F3c): un dividendo por usuario/simbolo/ex-date. */
  boolean existsByTransaction_User_IdAndTransaction_AssetSymbolAndExDividendDate(
      UUID userId, String assetSymbol, LocalDate exDividendDate);

  /**
   * Retencion por transaccion de los dividendos de un usuario (ADR-0014 F4): carga batch {@code
   * [transactionId, taxWithheld]} para enriquecer el snapshot sin N+1. El join por JPQL evita
   * materializar la entidad {@code Transaction}.
   */
  @Query(
      "SELECT d.transaction.transactionId, d.taxWithheld FROM DividendDetail d"
          + " WHERE d.transaction.user.id = :userId")
  List<Object[]> findTaxWithheldByUserId(@Param("userId") UUID userId);
}
