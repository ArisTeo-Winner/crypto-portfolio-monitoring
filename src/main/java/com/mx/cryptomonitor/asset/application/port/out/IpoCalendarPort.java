package com.mx.cryptomonitor.asset.application.port.out;

import java.time.LocalDate;
import java.util.List;

/** Puerto hacia el calendario de OPIs (nuevos listados en bolsa). */
public interface IpoCalendarPort {

  List<IpoEntry> getRecentIpos(LocalDate from, LocalDate to);

  record IpoEntry(
      String symbol,
      String name,
      String exchange,
      LocalDate date,
      String status,
      Long totalSharesValue) {}
}
