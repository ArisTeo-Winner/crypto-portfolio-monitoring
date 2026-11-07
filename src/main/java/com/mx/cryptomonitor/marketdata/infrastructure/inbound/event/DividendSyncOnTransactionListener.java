package com.mx.cryptomonitor.marketdata.infrastructure.inbound.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mx.cryptomonitor.marketdata.application.port.in.ReplayDividendAccrualUseCase;
import com.mx.cryptomonitor.marketdata.application.port.in.SyncDividendsUseCase;
import com.mx.cryptomonitor.shared.event.TransactionRegisteredEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-disparador de dividendos (ADR-0024): escucha el {@link TransactionRegisteredEvent} que
 * publica transaction al registrar una compra de un activo con dividendos (STOCK/ETF) y puebla su
 * historial llamando al {@link SyncDividendsUseCase}. Es el enganche automático que evita tener que
 * correr el endpoint admin a mano (y por tanto no requiere ROLE_ADMIN: el disparo es interno).
 *
 * <p>Corre DESPUÉS del commit del registro ({@code AFTER_COMMIT}) y de forma {@code @Async} en el
 * {@code dividendProcessingExecutor} (ADR-0028 F1: cola acotada + CallerRunsPolicy, nunca
 * descarta): el usuario obtiene su respuesta al instante y el poblado ocurre en segundo plano. En
 * su propia transacción ({@code REQUIRES_NEW}) y con el fallo contenido: si el proveedor externo
 * falla, NO revierte la transacción del usuario. El {@code sync} es idempotente (dedup por evento),
 * así que re-registrar el mismo símbolo no duplica dividendos.
 *
 * <p>El evento vive en {@code shared} para no crear un ciclo transaction↔marketdata (ver ADR-0024).
 */
@Component
@ConditionalOnProperty(
    name = "dividends.auto-sync-on-registration.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DividendSyncOnTransactionListener {

  private final SyncDividendsUseCase syncDividendsUseCase;
  private final ReplayDividendAccrualUseCase replayDividendAccrualUseCase;

  @Async("dividendProcessingExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onTransactionRegistered(TransactionRegisteredEvent event) {
    String symbol = event.assetSymbol();
    try {
      int created = syncDividendsUseCase.sync(symbol);
      if (created > 0) {
        log.info("Auto-poblado de dividendos para {}: {} evento(s) nuevo(s)", symbol, created);
      }
    } catch (RuntimeException ex) {
      log.error("Auto-poblado de dividendos falló para {}: {}", symbol, ex.getMessage());
    }
    // ADR-0027: acreditar al "late joiner" sobre los dividendos ya existentes del símbolo
    // (idempotente: no re-acredita a los demás). Independiente del sync para maximizar cobertura.
    try {
      replayDividendAccrualUseCase.replayForAccrual(symbol);
    } catch (RuntimeException ex) {
      log.error("Replay de accrual falló para {}: {}", symbol, ex.getMessage());
    }
  }
}
