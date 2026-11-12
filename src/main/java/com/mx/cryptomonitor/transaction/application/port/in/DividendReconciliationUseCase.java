package com.mx.cryptomonitor.transaction.application.port.in;

import java.util.UUID;

/**
 * Reconciliación de dividendos auto-acreditados al mutar la tenencia (SPEC reconciliación al
 * borrar). Tras eliminar/editar una operación que afecta la tenencia de un símbolo, re-deriva los
 * dividendos ACCRUED del usuario para ese símbolo desde el entitlement actual: elimina los que
 * quedan sin derecho (tenencia ≤ 0 en su ex-date) y recalcula el monto de los que cambian. Los
 * dividendos MANUAL nunca se tocan.
 */
public interface DividendReconciliationUseCase {

  void reconcileAccruedDividends(UUID userId, String assetSymbol);
}
