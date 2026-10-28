package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** Puerto hacia el cache Redis de la curva CETES de Banxico. */
public interface BanxicoCurveCachePort {

  Map<Integer, BigDecimal> readCurve();

  void writeCurve(Map<Integer, BigDecimal> curve, LocalDate auctionDate);

  Optional<LocalDate> readAuctionDate();
}
