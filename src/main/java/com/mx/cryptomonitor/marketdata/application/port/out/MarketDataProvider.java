package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface MarketDataProvider {

  Optional<BigDecimal> getLatest(String symbol);

  Optional<BigDecimal> getHistorical(String symbol, LocalDate date);
}
