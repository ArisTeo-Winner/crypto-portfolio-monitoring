package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.annotation.Cacheable;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class StockQuoteOrchestrator implements MarketDataProvider {

  private static final long FAILURE_LOG_WINDOW_MILLIS = 60_000L;

  private final List<StockQuoteProvider> providers;
  private final Map<String, FailureLogState> failureLogStates = new ConcurrentHashMap<>();

  @Override
  @Cacheable(cacheNames = "stockPrices", key = "#symbol.toUpperCase()", unless = "#result == null")
  public Optional<BigDecimal> getLatest(String symbol) {
    return executeFallback(symbol, provider -> provider.getLatest(symbol), "latest quote");
  }

  @Override
  @Cacheable(
      cacheNames = "historicalPrices",
      key = "#symbol.toUpperCase() + ':' + #date.toString()",
      unless = "#result == null")
  public Optional<BigDecimal> getHistorical(String symbol, LocalDate date) {
    return executeFallback(
        symbol, provider -> provider.getHistorical(symbol, date), "historical quote");
  }

  private Optional<BigDecimal> executeFallback(
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
          return result;
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
