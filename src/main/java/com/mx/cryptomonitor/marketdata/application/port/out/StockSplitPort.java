package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.List;

/**
 * Splits conocidos de un ticker, para que la valuacion ajuste titulos (ADR-0011). Lee de la cache
 * {@code stock_split} (fetch-on-miss al primer uso de un simbolo). Consumido por el modulo
 * portfolio.
 */
public interface StockSplitPort {

  List<StockSplitData> splitsFor(String ticker);
}
