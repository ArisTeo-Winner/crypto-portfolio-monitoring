package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.List;

/**
 * Proveedor externo del calendario de festivos US (ADR-0012, F4). Hay varias implementaciones
 * ordenadas por {@code @Order}: la de menor orden es la primaria y las siguientes son fallback.
 * Devuelve lista vacía si no está configurado o falla (el siguiente proveedor toma el relevo).
 */
public interface MarketHolidayProviderPort {

  List<MarketHolidayData> fetchHolidays();

  /** Nombre del proveedor para logging/observabilidad. */
  String providerName();
}
