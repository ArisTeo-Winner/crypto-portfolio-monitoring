package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Mercado bursátil con calendario propio (ADR-0012). Horarios en hora LOCAL del mercado; la {@link
 * ZoneId} resuelve DST automáticamente (y {@code America/Mexico_City} ya es CST fijo tras la
 * abolición del horario de verano en MX). {@code preOpen}/{@code afterClose} son null para mercados
 * sin horas extendidas minoristas (BMV).
 */
public enum Market {
  US_EQUITY(
      "NYSE",
      "Estados Unidos",
      "NYSE · NASDAQ",
      ZoneId.of("America/New_York"),
      LocalTime.of(9, 30),
      LocalTime.of(16, 0),
      LocalTime.of(4, 0),
      LocalTime.of(20, 0)),
  MX_BMV(
      "BMV",
      "México y SIC",
      "BMV · BIVA · SIC",
      ZoneId.of("America/Mexico_City"),
      LocalTime.of(8, 30),
      LocalTime.of(15, 0),
      null,
      null);

  private final String code;
  private final String label;
  private final String exchange;
  private final ZoneId zone;
  private final LocalTime regularOpen;
  private final LocalTime regularClose;
  private final LocalTime preOpen;
  private final LocalTime afterClose;

  Market(
      String code,
      String label,
      String exchange,
      ZoneId zone,
      LocalTime regularOpen,
      LocalTime regularClose,
      LocalTime preOpen,
      LocalTime afterClose) {
    this.code = code;
    this.label = label;
    this.exchange = exchange;
    this.zone = zone;
    this.regularOpen = regularOpen;
    this.regularClose = regularClose;
    this.preOpen = preOpen;
    this.afterClose = afterClose;
  }

  public String code() {
    return code;
  }

  public String label() {
    return label;
  }

  public String exchange() {
    return exchange;
  }

  public ZoneId zone() {
    return zone;
  }

  public LocalTime regularOpen() {
    return regularOpen;
  }

  public LocalTime regularClose() {
    return regularClose;
  }

  public LocalTime preOpen() {
    return preOpen;
  }

  public LocalTime afterClose() {
    return afterClose;
  }

  public boolean hasExtendedHours() {
    return preOpen != null && afterClose != null;
  }

  /** Enruta un activo a su mercado por moneda/exchange (ADR-0012). Crypto queda fuera (null). */
  public static Market fromCurrencyAndExchange(String currency, String exchange) {
    if ("BMV".equalsIgnoreCase(exchange) || "MXN".equalsIgnoreCase(currency)) {
      return MX_BMV;
    }
    if ("USD".equalsIgnoreCase(currency)) {
      return US_EQUITY;
    }
    return null;
  }
}
