package com.mx.cryptomonitor.asset.application.port.in;

/**
 * Registra la sucesión de una seguridad tras una fusión (ADR-0014 F6b-2): marca el predecesor como
 * {@code MUTED_BY_MERGER} y enlaza la sucesora a él ({@code predecessor_symbol}/{@code
 * predecessor_figi}). API pública de asset (named interface port.in) para que transaction la
 * invoque al acreditar la fusión. Best-effort por fila: solo actualiza las que existen en el
 * catálogo.
 */
public interface SecuritySuccessionPort {

  /**
   * @param predecessorSymbol ticker absorbido (se marca MUTED_BY_MERGER)
   * @param successorSymbol ticker sucesor (se enlaza al predecesor); {@code null} si es adquisición
   *     en efectivo sin sucesor
   */
  void recordMerger(String predecessorSymbol, String successorSymbol);
}
