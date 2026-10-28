package com.mx.cryptomonitor.marketdata.application.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Estado de los mercados para el indicador del frontend (ADR-0012). Horarios {@code regular}/{@code
 * extended} en HH:mm hora LOCAL del mercado; {@code asOf} y {@code nextChange.at} en instante UTC.
 */
public record MarketStatusResponse(Instant asOf, List<MarketStatusItem> markets) {

  public record MarketStatusItem(
      String code,
      String label,
      String exchange,
      String timezone,
      String phase,
      boolean isOpen,
      RegularHours regular,
      ExtendedHours extended,
      NextChange nextChange,
      String reasonCode) {}

  public record RegularHours(String open, String close) {}

  public record ExtendedHours(String pre, String after) {}

  public record NextChange(String type, Instant at) {}
}
