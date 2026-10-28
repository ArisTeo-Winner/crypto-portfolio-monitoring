package com.mx.cryptomonitor.marketdata.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.mx.cryptomonitor.marketdata.domain.model.BmvFxQuote;
import com.mx.cryptomonitor.marketdata.domain.model.BmvHistoricalPoint;
import com.mx.cryptomonitor.marketdata.domain.model.BmvIntradayPoint;
import com.mx.cryptomonitor.marketdata.domain.model.BmvQuote;
import com.mx.cryptomonitor.marketdata.domain.model.DataBursatilRate;

import reactor.core.publisher.Mono;

/** Puerto de salida hacia DataBursatil (cotizaciones y precios historicos de la BMV). */
public interface BmvMarketDataPort {

  Mono<Map<String, BmvQuote>> getQuotes(List<String> symbols, String bolsa);

  Mono<Map<LocalDate, BmvHistoricalPoint>> getHistory(
      String symbol, LocalDate inicio, LocalDate finalDate);

  Mono<List<BmvIntradayPoint>> getIntraday(String symbol);

  Mono<BmvFxQuote> getFxRate(String ticker);

  /** /v2/tasas — TIIE, CETES y tasa objetivo Banxico, keyed por nombre de serie. */
  Mono<Map<String, DataBursatilRate>> getRates();
}
