package com.mx.cryptomonitor.marketdata.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.mx.cryptomonitor.marketdata.domain.model.MergerType;

/**
 * Registra una fusión capturada por un operador (ADR-0015): persiste el evento MERGER + {@code
 * merger_action_detail} (idempotente por llave natural) y publica {@code MergerDetectedEvent} para
 * que transaction acredite a los tenedores. Devuelve el id del {@code corporate_action}.
 */
public interface RegisterMergerUseCase {

  UUID registerMerger(
      String predecessorSymbol,
      String successorSymbol,
      MergerType mergerType,
      BigDecimal exchangeRatio,
      BigDecimal cashPerShare,
      String currency,
      LocalDate effectiveDate);
}
