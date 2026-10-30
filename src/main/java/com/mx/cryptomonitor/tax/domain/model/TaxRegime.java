package com.mx.cryptomonitor.tax.domain.model;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Régimen fiscal MX por tipo de instrumento (ADR-0017). Determina la tasa cedular y, junto con el
 * mercado, el método de costeo. Fase 1 implementa la agregación de bolsa ({@link #MX_BOLSA}) con el
 * 10% del Art. 129 LISR; los demás quedan declarados para fases posteriores (dividendos F2,
 * interés/cripto F3) y hoy no llevan tasa.
 *
 * <p>No es asesoría fiscal: las tasas y artículos son base metodológica a validar con un
 * fiscalista.
 */
public enum TaxRegime {

  /** Acciones en bolsa BMV/SIC: 10% cedular sobre ganancia anual neta (Art. 129). */
  MX_BOLSA(new BigDecimal("0.10")),

  /** Acciones US (DriveWealth): ganancia realizada FIFO; acreditamiento extranjero (F2). */
  US_EQUITY(null),

  /** Dividendos: Art. 140 + acreditamiento W-8BEN (F2). */
  DIVIDEND(null),

  /** Intereses / CETES: interés real, Art. 133-135 (F3). */
  INTEREST(null),

  /** Cripto: enajenación de bienes, régimen aparte (F3). */
  CRYPTO(null),

  /** Instrumento sin régimen resuelto en esta fase. */
  UNSUPPORTED(null);

  private final BigDecimal cedularRate;

  TaxRegime(BigDecimal cedularRate) {
    this.cedularRate = cedularRate;
  }

  public Optional<BigDecimal> cedularRate() {
    return Optional.ofNullable(cedularRate);
  }
}
