package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.BmvMarketDataPort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.MxnSymbolLookupPort;
import com.mx.cryptomonitor.marketdata.domain.exception.DataBursatilException;
import com.mx.cryptomonitor.marketdata.domain.model.BmvFxQuote;
import com.mx.cryptomonitor.marketdata.domain.model.BmvQuote;
import com.mx.cryptomonitor.marketdata.domain.model.HybridPriceQuote;
import com.mx.cryptomonitor.marketdata.domain.model.MarketFxSnapshotEntity;
import com.mx.cryptomonitor.marketdata.domain.model.MarketPriceSnapshotEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketFxSnapshotRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketPriceSnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Selecciona el proveedor de cotizaciones segun la moneda del activo: DataBursatil/BMV para MXN, el
 * orquestador de proveedores existente (Twelve Data/Alpha Vantage/Massive) para USD.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridQuoteService {

  private static final String MXN = "MXN";
  private static final String BMV_EXCHANGE = "BMV";
  private static final String USD_MXN_TICKER = "USDMXN";
  private static final String PROVIDER = "DATABURSATIL";
  private static final int BATCH_SIZE = 50;

  private final BmvMarketDataPort databursatil;
  private final MarketDataProvider stockQuoteOrchestrator;
  private final MxnSymbolLookupPort mxnSymbolLookupPort;
  private final MarketPriceSnapshotRepository snapshotRepository;
  private final MarketFxSnapshotRepository fxSnapshotRepository;

  @Value("${marketdata.databursatil.refresh.enabled:true}")
  private boolean scheduledRefreshEnabled;

  /** Precio actual: MXN via DataBursatil/BMV, USD via el orquestador de proveedores existente. */
  public HybridPriceQuote getCurrentPrice(String symbol, String currency, String exchange) {
    if (MXN.equalsIgnoreCase(currency)) {
      return getMxnPrice(symbol, exchange);
    }
    return getUsdPrice(symbol);
  }

  private HybridPriceQuote getMxnPrice(String symbol, String exchange) {
    String bmvSymbol = toBmvSymbol(symbol);
    String bolsa = (exchange == null || exchange.isBlank()) ? BMV_EXCHANGE : exchange;

    Map<String, BmvQuote> quotes = databursatil.getQuotes(List.of(bmvSymbol), bolsa).block();
    BmvQuote quote = quotes == null ? null : quotes.get(bmvSymbol);
    if (quote == null) {
      throw new DataBursatilException("DataBursatil no devolvio cotizacion para " + bmvSymbol);
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    saveSnapshot(symbol, bolsa, MXN, quote, now);
    return new HybridPriceQuote(symbol, BigDecimal.valueOf(quote.u()), MXN, PROVIDER, now);
  }

  private HybridPriceQuote getUsdPrice(String symbol) {
    BigDecimal price =
        stockQuoteOrchestrator.getLatest(symbol).orElseThrow(NoSuchElementException::new);
    return new HybridPriceQuote(
        symbol, price, "USD", "orchestrator", OffsetDateTime.now(ZoneOffset.UTC));
  }

  /** Tipo de cambio USD/MXN actual (DataBursatil /divisas), cacheado en market_fx_snapshot. */
  public BigDecimal getUsdMxnRate() {
    BmvFxQuote fx = databursatil.getFxRate(USD_MXN_TICKER).block();
    if (fx == null) {
      throw new DataBursatilException("DataBursatil no devolvio tipo de cambio USD/MXN");
    }
    BigDecimal rate = BigDecimal.valueOf(fx.u());

    MarketFxSnapshotEntity snapshot =
        MarketFxSnapshotEntity.builder()
            .ticker(USD_MXN_TICKER)
            .rate(rate)
            .absChange(BigDecimal.valueOf(fx.c()))
            .pctChange(BigDecimal.valueOf(fx.m()))
            .provider(PROVIDER)
            .quoteAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();
    fxSnapshotRepository.save(snapshot);

    return rate;
  }

  /** Refresco batch de todos los simbolos MXN en uso, en lotes de {@value #BATCH_SIZE}. */
  public void refreshAllMxnPortfolios() {
    List<String> symbols = mxnSymbolLookupPort.findDistinctMxnSymbols();
    if (symbols.isEmpty()) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (int start = 0; start < symbols.size(); start += BATCH_SIZE) {
      List<String> batch = symbols.subList(start, Math.min(start + BATCH_SIZE, symbols.size()));
      List<String> bmvBatch = batch.stream().map(this::toBmvSymbol).toList();

      Map<String, BmvQuote> quotes = databursatil.getQuotes(bmvBatch, BMV_EXCHANGE).block();
      if (quotes == null) {
        continue;
      }
      quotes.forEach(
          (bmvSymbol, quote) ->
              saveSnapshot(fromBmvSymbol(bmvSymbol), BMV_EXCHANGE, MXN, quote, now));
    }
  }

  @Scheduled(cron = "${marketdata.databursatil.refresh.cron:0 */5 9-15 * * MON-FRI}")
  public void scheduledRefresh() {
    if (!scheduledRefreshEnabled) {
      return;
    }
    try {
      refreshAllMxnPortfolios();
    } catch (RuntimeException ex) {
      log.warn("Refresco periodico de cotizaciones MXN fallo", ex);
    }
  }

  private void saveSnapshot(
      String symbol, String exchange, String currency, BmvQuote quote, OffsetDateTime timestamp) {
    // Concepto DataBursatil: u=ultimo p=promedio a=apertura x=maximo n=minimo
    // c=cambio m=cambio% v=volumen i=importe operado
    MarketPriceSnapshotEntity snapshot =
        MarketPriceSnapshotEntity.builder()
            .assetSymbol(symbol)
            .exchange(exchange)
            .currency(currency)
            .priceClose(BigDecimal.valueOf(quote.u()))
            .priceOpen(BigDecimal.valueOf(quote.a()))
            .priceHigh(BigDecimal.valueOf(quote.x()))
            .priceLow(BigDecimal.valueOf(quote.n()))
            .priceAvg(BigDecimal.valueOf(quote.p()))
            .priceChange(BigDecimal.valueOf(quote.c()))
            .pctChange(BigDecimal.valueOf(quote.m()))
            .volume((long) quote.v())
            .importeOperado(BigDecimal.valueOf(quote.i()))
            .provider(PROVIDER)
            .quoteTimestamp(timestamp)
            .build();
    snapshotRepository.save(snapshot);
  }

  private String toBmvSymbol(String symbol) {
    String upper = symbol.trim().toUpperCase(Locale.ROOT);
    return upper.endsWith("*") ? upper : upper + "*";
  }

  private String fromBmvSymbol(String bmvSymbol) {
    return bmvSymbol.endsWith("*") ? bmvSymbol.substring(0, bmvSymbol.length() - 1) : bmvSymbol;
  }
}
