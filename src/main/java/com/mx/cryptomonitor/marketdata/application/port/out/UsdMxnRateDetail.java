package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Tasa USD/MXN con su procedencia: valor, proveedor (BANXICO/DATABURSATIL) y fecha de cotizacion.
 */
public record UsdMxnRateDetail(BigDecimal rate, String provider, OffsetDateTime asOf) {}
