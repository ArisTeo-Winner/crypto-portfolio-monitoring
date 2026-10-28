package com.mx.cryptomonitor.portfolio.application.port.in;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Posición de un usuario en una seguridad (ADR-0014 F6b-2): cantidad + costo base, desde la fuente
 * de verdad de portfolio (ACB). API pública de portfolio (named interface port.in) para que
 * transaction calcule el carryover basis de una fusión sin acoplarse al modelo de dominio de
 * portfolio. Devuelve un DTO plano, no la entidad.
 */
public interface SecurityPositionPort {

  Optional<SecurityPosition> positionOf(UUID userId, String assetSymbol);

  /** Cantidad total y costo base (total invertido) de la posición. */
  record SecurityPosition(BigDecimal quantity, BigDecimal costBasis) {}
}
