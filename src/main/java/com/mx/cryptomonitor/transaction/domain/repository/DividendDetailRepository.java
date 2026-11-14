package com.mx.cryptomonitor.transaction.domain.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

  /**
   * Ingresos por dividendo del usuario ya recibidos (ADR-0029): detalle + su transaccion DIVIDEND,
   * solo con fecha de pago (transactionDate) &lt;= cutoff (los futuros aun no se cobraron). JOIN
   * FETCH evita N+1 al leer la transaccion de cada fila. Orden: pago mas reciente primero.
   */
  @Query(
      "SELECT d FROM DividendDetail d JOIN FETCH d.transaction t"
          + " WHERE t.user.id = :userId AND t.transactionDate <= :cutoff"
          + " ORDER BY t.transactionDate DESC")
  List<DividendDetail> findUserDividends(
      @Param("userId") UUID userId, @Param("cutoff") OffsetDateTime cutoff);

  /**
   * Dividendos AUTO-acreditados de un usuario para un símbolo (SPEC reconciliación al borrar). JOIN
   * FETCH de la transacción para re-derivar/eliminar sin N+1. Solo {@code origin}=ACCRUED: los
   * manuales nunca se reconcilian.
   */
  @Query(
      "SELECT d FROM DividendDetail d JOIN FETCH d.transaction t"
          + " WHERE t.user.id = :userId AND t.assetSymbol = :symbol"
          + " AND d.origin = com.mx.cryptomonitor.transaction.domain.model.DividendOrigin.ACCRUED")
  List<DividendDetail> findAccruedByUserAndSymbol(
      @Param("userId") UUID userId, @Param("symbol") String symbol);
}
