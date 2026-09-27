package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Proveedor de la serie historica del tipo de cambio USD/MXN por rango de fechas. Implementacion:
 * Banxico SIE (FIX, serie SF43718) via /datos/{fechaInicio}/{fechaFin}. Devuelve solo los dias con
 * publicacion (habiles) del rango, mapeados por su fecha de determinacion.
 */
public interface UsdMxnFxRateHistoryProviderPort {

  Map<LocalDate, BigDecimal> fetchUsdMxnRates(LocalDate from, LocalDate to);
}
