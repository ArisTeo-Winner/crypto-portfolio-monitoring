package com.mx.cryptomonitor.transaction.infrastructure.inbound.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mx.cryptomonitor.marketdata.application.port.out.MergerDetectedEvent;
import com.mx.cryptomonitor.transaction.application.port.in.MergerAccrualUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Trigger inbound del accrual de fusiones (ADR-0015): escucha el {@link MergerDetectedEvent} que
 * publica marketdata al registrar una fusión y dispara el carryover por usuario (F6b-2). Corre
 * DESPUÉS del commit del registro y en su propia transacción: un fallo del accrual no revierte la
 * persistencia del evento. marketdata NO depende de transaction (la relación es solo este evento).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MergerAccrualEventListener {

  private final MergerAccrualUseCase mergerAccrualUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onMergerDetected(MergerDetectedEvent event) {
    try {
      mergerAccrualUseCase.accrue(
          event.predecessorSymbol(),
          event.successorSymbol(),
          event.effectiveDate(),
          event.exchangeRatio(),
          event.cashPerShare(),
          event.currency());
    } catch (RuntimeException ex) {
      log.error(
          "Accrual de fusión falló para {}→{}: {}",
          event.predecessorSymbol(),
          event.successorSymbol(),
          ex.getMessage());
    }
  }
}
