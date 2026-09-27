package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Proveedor primario del tipo de cambio USD/MXN para la valuacion. Implementacion actual: Banxico
 * SIE (FIX, serie SF43718). Devuelve vacio si el proveedor no tiene dato; el orquestador ({@code
 * HybridQuoteService}) cae a DataBursatil como secundario cuando esto ocurre o cuando el proveedor
 * primario falla.
 */
public interface UsdMxnFxRateProviderPort {

  Optional<BigDecimal> fetchUsdMxnRate();

  String providerName();
}
