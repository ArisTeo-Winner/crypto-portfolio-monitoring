package com.mx.cryptomonitor.marketdata.infrastructure.inbound.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mx.cryptomonitor.marketdata.application.service.DividendSyncJobLifecycleService;
import com.mx.cryptomonitor.shared.event.TransactionRegisteredEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-disparador de dividendos (ADR-0024): escucha el {@link TransactionRegisteredEvent} que
 * publica transaction al registrar una compra de un activo con dividendos (STOCK/ETF) y ENCOLA el
 * símbolo en la cola durable de dividendos (ADR-0028 F2). Es el enganche automático que evita
 * correr el endpoint admin a mano (y no requiere ROLE_ADMIN: el disparo es interno).
 *
 * <p>Corre DESPUÉS del commit del registro ({@code AFTER_COMMIT}) y de forma {@code @Async} en el
 * {@code dividendProcessingExecutor} (ADR-0028 F1: cola acotada + CallerRunsPolicy, nunca
 * descarta). Solo ENCOLA (insert rápido, idempotente/dedup por símbolo activo); el trabajo pesado
 * (sync + replay de accrual) lo hace el {@code DividendSyncJobWorker} de forma DURABLE,
 * sobreviviendo reinicios y reintentando — así una ráfaga de registros nunca pierde la
 * acreditación. Fallo contenido: no revierte la transacción del usuario.
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

  private final DividendSyncJobLifecycleService jobLifecycleService;

  @Async("dividendProcessingExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onTransactionRegistered(TransactionRegisteredEvent event) {
    try {
      jobLifecycleService.enqueue(event.assetSymbol());
    } catch (RuntimeException ex) {
      log.error(
          "No se pudo encolar el procesamiento de dividendos para {}: {}",
          event.assetSymbol(),
          ex.getMessage());
    }
  }
}
