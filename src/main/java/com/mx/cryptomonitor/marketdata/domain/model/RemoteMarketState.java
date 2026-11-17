package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.Instant;

/**
 * Estado de un mercado según una fuente externa (ADR-0033). {@code opensAt} es la campana de
 * apertura como instante absoluto y solo viene informada con {@link Status#CLOSED}: la cuenta
 * regresiva del proveedor envejece cada segundo y nunca se guarda tal cual.
 */
public record RemoteMarketState(Status status, Instant opensAt) {

  public enum Status {
    OPEN,
    CLOSED,
    UNAVAILABLE
  }

  public static RemoteMarketState open() {
    return new RemoteMarketState(Status.OPEN, null);
  }

  public static RemoteMarketState closedUntil(Instant opensAt) {
    return new RemoteMarketState(Status.CLOSED, opensAt);
  }

  public static RemoteMarketState unavailable() {
    return new RemoteMarketState(Status.UNAVAILABLE, null);
  }
}
