package com.mx.cryptomonitor.marketdata.infrastructure.outbound.dividend;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderUnavailableException;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage.AlphaVantageDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd.EodhdDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.fmp.FmpDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveDividendAdapter;

/**
 * Orquestador de proveedores de dividendos (ADR-0014 F3b + addendum ADR-0016). Consulta en orden y
 * devuelve el PRIMER resultado no vacío:
 *
 * <ol>
 *   <li>Massive — US primario (NASDAQ/NYSE).
 *   <li>FMP — fallback US ({@code /stable/dividends}); el más completo (incluye el dividendo futuro
 *       anunciado y {@code frequency}). Rellena huecos de Massive (p. ej. {@code GOOG}).
 *   <li>AlphaVantage — fallback US secundario ({@code DIVIDENDS}); solo histórico (sin el futuro).
 *   <li>DataBursatil — BMV en MXN nativo ({@code /v2/emisoras}, histórico + próximo con ex-date).
 *   <li>EODHD — ÚLTIMO recurso (su plan actual no autoriza {@code /api/div}, 403).
 * </ol>
 *
 * <p>Los proveedores US van antes que DataBursatil para no gastar créditos BMV en símbolos US. Es
 * {@code @Primary} para que {@code DividendSyncService} lo inyecte sin ambigüedad frente a los
 * adapters concretos (que siguen siendo beans para poder orquestarlos). Cada adapter ya devuelve
 * lista vacía ante fallo/no-configurado, así que la orquestación nunca rompe la ingesta.
 */
@Component
@Primary
public class DividendProviderOrchestrator implements DividendProviderPort {

  private final List<DividendProviderPort> usFirst;
  private final List<DividendProviderPort> mxnFirst;
  private final AssetCatalogQueryPort assetCatalog;

  public DividendProviderOrchestrator(
      MassiveDividendAdapter massiveDividendAdapter,
      FmpDividendAdapter fmpDividendAdapter,
      AlphaVantageDividendAdapter alphaVantageDividendAdapter,
      DataBursatilDividendAdapter dataBursatilDividendAdapter,
      EodhdDividendAdapter eodhdDividendAdapter,
      AssetCatalogQueryPort assetCatalog) {
    // Orden US (default): Massive → FMP → AlphaVantage → DataBursatil → EODHD.
    this.usFirst =
        List.of(
            massiveDividendAdapter,
            fmpDividendAdapter,
            alphaVantageDividendAdapter,
            dataBursatilDividendAdapter,
            eodhdDividendAdapter);
    // ADR-0025 F3: para MXN/BMV se consulta DataBursatil PRIMERO (no gasta cuota US); los US quedan
    // de respaldo y EODHD último, como siempre.
    this.mxnFirst =
        List.of(
            dataBursatilDividendAdapter,
            massiveDividendAdapter,
            fmpDividendAdapter,
            alphaVantageDividendAdapter,
            eodhdDividendAdapter);
    this.assetCatalog = assetCatalog;
  }

  @Override
  public List<DividendEventData> fetchDividends(String ticker) {
    boolean anyProviderFailed = false;
    for (DividendProviderPort provider : resolveOrder(ticker)) {
      try {
        List<DividendEventData> events = provider.fetchDividends(ticker);
        if (events != null && !events.isEmpty()) {
          return events;
        }
      } catch (RuntimeException ex) {
        // Un proveedor no concluyó (ADR-0025 F2): se intenta el siguiente, pero se recuerda que
        // el "vacío" final no es un "no paga" confiable.
        anyProviderFailed = true;
      }
    }
    if (anyProviderFailed) {
      throw new DividendProviderUnavailableException(
          "Ningún proveedor concluyó para " + ticker + " (al menos uno falló)");
    }
    return List.of();
  }

  /**
   * ADR-0025 F3: elige el orden de proveedores según la moneda del catálogo. {@code MXN} → BMV →
   * DataBursatil primero; cualquier otra (o desconocida) → orden US por defecto.
   */
  private List<DividendProviderPort> resolveOrder(String ticker) {
    boolean mxn =
        assetCatalog
            .findCurrencyBySymbol(ticker)
            .map(currency -> currency.equalsIgnoreCase("MXN"))
            .orElse(false);
    return mxn ? mxnFirst : usFirst;
  }
}
