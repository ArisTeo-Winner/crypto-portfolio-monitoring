package com.mx.cryptomonitor.transaction.domain.spinoff;

import java.math.BigDecimal;

/**
 * Efecto de un spinoff sobre la posición de un usuario en la matriz (ADR-0014 F6e-2). La matriz
 * conserva la MISMA cantidad pero con {@code retainedParentBasis}; el tenedor recibe {@code
 * spincoQuantity} de la spinco con {@code spincoBasis}. Invariante: {@code retainedParentBasis +
 * spincoBasis == baseOriginal} (reparto sin realizar PnL).
 */
public record SpinoffEffect(
    BigDecimal retainedParentBasis, BigDecimal spincoQuantity, BigDecimal spincoBasis) {}
