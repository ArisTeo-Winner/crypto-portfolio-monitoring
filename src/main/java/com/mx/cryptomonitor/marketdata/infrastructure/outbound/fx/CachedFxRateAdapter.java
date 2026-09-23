package com.mx.cryptomonitor.marketdata.infrastructure.outbound.fx;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.domain.model.MarketFxSnapshotEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketFxSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lee la tasa USD/MXN mas reciente cacheada en {@code market_fx_snapshot} (poblada por {@code
 * HybridQuoteService} desde DataBursatil). Lectura barata (query indexada), sin llamada externa en
 * el hot path de valuacion. Vacio si aun no hay snapshot => el consumidor no convierte.
 */
@Component
@RequiredArgsConstructor
public class CachedFxRateAdapter implements FxRatePort {

  private static final String USD_MXN_TICKER = "USDMXN";

  private final MarketFxSnapshotRepository fxSnapshotRepository;

  @Override
  public Optional<BigDecimal> usdMxnRate() {
    return fxSnapshotRepository
        .findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER)
        .map(MarketFxSnapshotEntity::getRate);
  }
}
