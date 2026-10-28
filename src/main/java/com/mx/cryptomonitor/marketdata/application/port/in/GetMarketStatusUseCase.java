package com.mx.cryptomonitor.marketdata.application.port.in;

import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse;

/** Estado actual de los mercados (ADR-0012), computado del calendario. */
public interface GetMarketStatusUseCase {

  MarketStatusResponse currentStatus();
}
