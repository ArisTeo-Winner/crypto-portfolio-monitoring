package com.mx.cryptomonitor.marketdata.infrastructure.outbound.dividend;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd.EodhdDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveDividendAdapter;

/**
 * Orquestador de proveedores de dividendos (ADR-0014 F3b + addendum ADR-0016). Consulta en orden y
 * devuelve el PRIMER resultado no vacío:
 *
 * <ol>
 *   <li>Massive — US (proveedor ya probado para NASDAQ/NYSE).
 *   <li>DataBursatil — BMV en MXN nativo ({@code /v2/emisoras}, histórico + próximo con ex-date).
 *   <li>EODHD — ÚLTIMO recurso (su plan actual no autoriza {@code /api/div}, 403).
 * </ol>
 *
 * <p>Es {@code @Primary} para que {@code DividendSyncService} lo inyecte sin ambigüedad frente a
 * los adapters concretos (que siguen siendo beans para poder orquestarlos). Cada adapter ya
 * devuelve lista vacía ante fallo/no-configurado, así que la orquestación nunca rompe la ingesta.
 */
@Component
@Primary
public class DividendProviderOrchestrator implements DividendProviderPort {

  private final List<DividendProviderPort> providers;

  public DividendProviderOrchestrator(
      MassiveDividendAdapter massiveDividendAdapter,
      DataBursatilDividendAdapter dataBursatilDividendAdapter,
      EodhdDividendAdapter eodhdDividendAdapter) {
    this.providers =
        List.of(massiveDividendAdapter, dataBursatilDividendAdapter, eodhdDividendAdapter);
  }

  @Override
  public List<DividendEventData> fetchDividends(String ticker) {
    for (DividendProviderPort provider : providers) {
      List<DividendEventData> events = provider.fetchDividends(ticker);
      if (events != null && !events.isEmpty()) {
        return events;
      }
    }
    return List.of();
  }
}
