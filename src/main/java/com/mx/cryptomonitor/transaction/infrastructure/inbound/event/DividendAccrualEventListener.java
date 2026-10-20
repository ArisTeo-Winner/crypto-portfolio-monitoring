package com.mx.cryptomonitor.transaction.infrastructure.inbound.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendDetectedEvent;
import com.mx.cryptomonitor.transaction.application.port.in.DividendAccrualUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Trigger inbound del accrual de dividendos (ADR-0014 F3c): escucha el {@link
 * DividendDetectedEvent} que publica marketdata y dispara la acreditacion por usuario. Corre
 * <b>despues del commit</b> de la deteccion y en su propia transaccion, para que un fallo del
 * accrual no afecte la persistencia del evento detectado. Los dividendos son equities (feed
 * Massive): el tipo de activo se asume STOCK.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DividendAccrualEventListener {

  private static final String ASSET_TYPE = "STOCK";

  private final DividendAccrualUseCase accrualService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onDividendDetected(DividendDetectedEvent event) {
    try {
      accrualService.accrue(
          event.assetSymbol(),
          ASSET_TYPE,
          event.exDividendDate(),
          event.paymentDate(),
          event.dividendPerShare(),
          event.currency(),
          null);
    } catch (RuntimeException ex) {
      log.error(
          "Accrual de dividendo fallo para symbol={} exDate={}: {}",
          event.assetSymbol(),
          event.exDividendDate(),
          ex.getMessage());
    }
  }
}
