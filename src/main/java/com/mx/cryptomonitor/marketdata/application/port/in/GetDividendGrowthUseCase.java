package com.mx.cryptomonitor.marketdata.application.port.in;

import com.mx.cryptomonitor.marketdata.application.dto.response.DividendGrowthResponse;

/** Crecimiento del dividendo (CAGR 1/3/5/10a) de una seguridad (ADR-0014 F5). */
public interface GetDividendGrowthUseCase {

  DividendGrowthResponse growthFor(String symbol);
}
