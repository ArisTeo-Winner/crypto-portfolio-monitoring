package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.annotation.Cacheable;

import com.mx.cryptomonitor.marketdata.application.port.out.LastKnownQuotePort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.OfficialClosePort;
import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;
import com.mx.cryptomonitor.marketdata.domain.model.ClosedWindow;
import com.mx.cryptomonitor.marketdata.domain.model.HeldQuoteSource;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.StockQuote;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Cadena de proveedores de cotización de acciones con fallback.
 *
 * <p>{@link #getLatest(String)} aplica además ADR-0033 detrás de la caché {@code stockPrices}: con
 * el mercado cerrado devuelve el cierre retenido sin consultar proveedores, y ante un fallo total
 * devuelve el último precio conocido en lugar de un error. Ambas piezas son opcionales; sin ellas
 * el comportamiento es el de la cadena sola.
 */
@Slf4j
public class StockQuoteOrchestrator implements MarketDataProvider {

  private static final long FAILURE_LOG_WINDOW_MILLIS = 60_000L;
  private static final String SERVED_METRIC = "marketdata.quote.served";
  // La cadena solo cotiza listados de EE. UU.; cambia cuando ADR-0016 Fase 2 enrute por mercado.
  private static final Market QUOTE_MARKET = Market.US_EQUITY;

  private final List<StockQuoteProvider> providers;
  private final LastKnownQuotePort quoteStore;
  private final ClosedMarketQuotePolicy closedMarketPolicy;
  private final OfficialClosePort officialClose;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final Map<String, FailureLogState> failureLogStates = new ConcurrentHashMap<>();

  public StockQuoteOrchestrator(List<StockQuoteProvider> providers) {
    this(providers, null, null, null, new SimpleMeterRegistry(), Clock.systemUTC());
  }

  /**
   * @param quoteStore almacén de último precio conocido; {@code null} desactiva ADR-0033
   * @param closedMarketPolicy regla de retención; {@code null} desactiva solo la retención
   * @param officialClose confirmación del cierre por fecha; {@code null} deja solo el margen
   */
  public StockQuoteOrchestrator(
      List<StockQuoteProvider> providers,
      LastKnownQuotePort quoteStore,
      ClosedMarketQuotePolicy closedMarketPolicy,
      OfficialClosePort officialClose,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.providers = providers;
    this.quoteStore = quoteStore;
    this.closedMarketPolicy = closedMarketPolicy;
    this.officialClose = officialClose;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  @Override
  @Cacheable(cacheNames = "stockPrices", key = "#symbol.toUpperCase()", unless = "#result == null")
  public Optional<BigDecimal> getLatest(String symbol) {
    if (quoteStore == null) {
      return executeFallback(symbol, provider -> provider.getLatest(symbol), "latest quote")
          .map(ProviderQuote::price);
    }

    String key = symbol.toUpperCase();
    Optional<StockQuote> held = quoteStore.findHeld(key);
    if (held.isPresent()) {
      served("held_market_closed");
      return held.map(StockQuote::price);
    }

    Optional<ClosedWindow> window = closedWindow();
    Optional<StockQuote> official = window.flatMap(w -> confirmedClose(key, w));
    if (official.isPresent()) {
      quoteStore.saveLast(key, official.get());
      quoteStore.hold(key, official.get(), HeldQuoteSource.OFFICIAL_EOD, window.get().opensAt());
      served("official_close");
      return official.map(StockQuote::price);
    }

    Optional<ProviderQuote> fromChain;
    try {
      fromChain = executeFallback(symbol, provider -> provider.getLatest(symbol), "latest quote");
    } catch (ExternalProviderInvalidSymbolException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      Optional<BigDecimal> last = lastKnown(key);
      if (last.isEmpty()) {
        throw ex;
      }
      return last;
    }
    if (fromChain.isEmpty()) {
      return lastKnown(key);
    }

    StockQuote quote =
        new StockQuote(fromChain.get().price(), clock.instant(), fromChain.get().provider(), null);
    quoteStore.saveLast(key, quote);
    if (window.isPresent() && window.get().settled()) {
      quoteStore.hold(key, quote, HeldQuoteSource.SETTLED_CHAIN, window.get().opensAt());
    }
    served("provider");
    return Optional.of(quote.price());
  }

  @Override
  @Cacheable(
      cacheNames = "historicalPrices",
      key = "#symbol.toUpperCase() + ':' + #date.toString()",
      unless = "#result == null")
  public Optional<BigDecimal> getHistorical(String symbol, LocalDate date) {
    return executeFallback(
            symbol, provider -> provider.getHistorical(symbol, date), "historical quote")
        .map(ProviderQuote::price);
  }

  private Optional<ClosedWindow> closedWindow() {
    if (closedMarketPolicy == null) {
      return Optional.empty();
    }
    try {
      return closedMarketPolicy.closedWindow(QUOTE_MARKET);
    } catch (RuntimeException ex) {
      // El calendario lee market_holiday de la base: si falla, se cotiza como siempre.
      log.warn(
          "No se pudo evaluar el cierre de mercado; se consulta a proveedores: {}", ex.toString());
      return Optional.empty();
    }
  }

  private Optional<StockQuote> confirmedClose(String key, ClosedWindow window) {
    if (officialClose == null) {
      return Optional.empty();
    }
    return officialClose
        .find(key)
        .filter(quote -> window.sessionDate().equals(quote.sessionDate()));
  }

  private Optional<BigDecimal> lastKnown(String key) {
    Optional<StockQuote> last = quoteStore.findLast(key);
    last.ifPresent(
        quote -> {
          log.warn(
              "Sin precio de proveedores para {}: se sirve el ultimo conocido ({}, obtenido {}).",
              key,
              quote.provider(),
              quote.fetchedAt());
          served("last_known_fallback");
        });
    return last.map(StockQuote::price);
  }

  private void served(String source) {
    meterRegistry.counter(SERVED_METRIC, "source", source).increment();
  }

  private Optional<ProviderQuote> executeFallback(
      String symbol, ProviderCall providerCall, String operation) {
    RuntimeException firstRecoverableFailure = null;

    for (StockQuoteProvider provider : providers) {
      try {
        Optional<BigDecimal> result = providerCall.invoke(provider);
        if (result.isPresent()) {
          if (firstRecoverableFailure != null) {
            log.info(
                "Proveedor {} resolvio {} para {} despues de fallback.",
                provider.providerName(),
                operation,
                symbol);
          }
          return Optional.of(new ProviderQuote(result.get(), provider.providerName()));
        }

        log.info(
            "Proveedor {} no devolvio {} para {}. Intentando siguiente proveedor si existe.",
            provider.providerName(),
            operation,
            symbol);
      } catch (ExternalProviderInvalidSymbolException ex) {
        log.warn(
            "Proveedor {} reporto simbolo invalido para {}. Se detiene el fallback.",
            provider.providerName(),
            symbol);
        throw ex;
      } catch (RuntimeException ex) {
        if (firstRecoverableFailure == null) {
          firstRecoverableFailure = ex;
        }
        logRecoverableFailure(provider.providerName(), operation, symbol, ex);
      }
    }

    if (firstRecoverableFailure != null) {
      throw firstRecoverableFailure;
    }

    return Optional.empty();
  }

  @FunctionalInterface
  private interface ProviderCall {
    Optional<BigDecimal> invoke(StockQuoteProvider provider);
  }

  private record ProviderQuote(BigDecimal price, String provider) {}

  private void logRecoverableFailure(
      String providerName, String operation, String symbol, RuntimeException ex) {
    String failureKind = classifyFailure(ex);
    String key = providerName + "|" + operation + "|" + symbol + "|" + failureKind;
    long now = System.currentTimeMillis();
    FailureLogState state = failureLogStates.computeIfAbsent(key, ignored -> new FailureLogState());

    synchronized (state) {
      if (now - state.lastLoggedAtMillis < FAILURE_LOG_WINDOW_MILLIS) {
        state.suppressedCount++;
        return;
      }

      if (state.suppressedCount > 0) {
        log.warn(
            "Proveedor {} {} al consultar {} para {}. Intentando fallback. {} eventos repetidos fueron suprimidos en los ultimos {} segundos.",
            providerName,
            failureKind,
            operation,
            symbol,
            state.suppressedCount,
            FAILURE_LOG_WINDOW_MILLIS / 1000);
      } else {
        log.warn(
            "Proveedor {} {} al consultar {} para {}. Intentando fallback.",
            providerName,
            failureKind,
            operation,
            symbol);
      }

      state.lastLoggedAtMillis = now;
      state.suppressedCount = 0;
    }
  }

  private String classifyFailure(RuntimeException ex) {
    if (ex instanceof ExternalProviderRateLimitException) {
      return "alcanzó su limite de peticiones";
    }
    if (ex instanceof ExternalProviderUpstreamException) {
      return "presentó un error temporal del proveedor";
    }
    return "falló";
  }

  private static final class FailureLogState {
    private long lastLoggedAtMillis;
    private int suppressedCount;
  }
}
