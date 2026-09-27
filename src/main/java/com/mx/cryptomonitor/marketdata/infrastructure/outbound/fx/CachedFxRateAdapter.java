package com.mx.cryptomonitor.marketdata.infrastructure.outbound.fx;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.UsdMxnRateDetail;
import com.mx.cryptomonitor.marketdata.application.service.HybridQuoteService;
import com.mx.cryptomonitor.marketdata.domain.model.MarketFxSnapshotEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketFxSnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tasa USD/MXN para la valuacion. Sirve la ultima cacheada en {@code market_fx_snapshot} cuando
 * esta fresca (lectura barata, sin llamada externa en el hot path). Si el cache esta vacio o
 * vencido, refresca en vivo via {@link HybridQuoteService#getUsdMxnRate()} (que persiste el
 * snapshot para las siguientes lecturas). Si el refresco falla, cae al cache vencido; si tampoco
 * hay, devuelve vacio y el consumidor no convierte (sin regresion frente al comportamiento previo).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CachedFxRateAdapter implements FxRatePort {

  private static final String USD_MXN_TICKER = "USDMXN";

  private final MarketFxSnapshotRepository fxSnapshotRepository;
  private final HybridQuoteService hybridQuoteService;

  @Value("${marketdata.fx.cache-ttl-minutes:60}")
  private long cacheTtlMinutes;

  @Override
  public Optional<BigDecimal> usdMxnRate() {
    Optional<MarketFxSnapshotEntity> cached =
        fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER);
    if (cached.filter(this::isFresh).isPresent()) {
      return cached.map(MarketFxSnapshotEntity::getRate);
    }
    return refreshLive().or(() -> cached.map(MarketFxSnapshotEntity::getRate));
  }

  @Override
  public Optional<UsdMxnRateDetail> usdMxnRateDetail() {
    Optional<MarketFxSnapshotEntity> cached =
        fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER);
    if (cached.filter(this::isFresh).isPresent()) {
      return cached.map(CachedFxRateAdapter::toDetail);
    }
    try {
      if (hybridQuoteService.getUsdMxnRate() != null) {
        Optional<MarketFxSnapshotEntity> refreshed =
            fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER);
        if (refreshed.isPresent()) {
          return refreshed.map(CachedFxRateAdapter::toDetail);
        }
      }
    } catch (RuntimeException ex) {
      log.warn(
          "Refresco en vivo de USD/MXN (detalle) fallo; se usara el cache vencido si existe", ex);
    }
    return cached.map(CachedFxRateAdapter::toDetail);
  }

  private static UsdMxnRateDetail toDetail(MarketFxSnapshotEntity snapshot) {
    return new UsdMxnRateDetail(snapshot.getRate(), snapshot.getProvider(), snapshot.getQuoteAt());
  }

  private boolean isFresh(MarketFxSnapshotEntity snapshot) {
    OffsetDateTime quoteAt = snapshot.getQuoteAt();
    return quoteAt != null
        && quoteAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(cacheTtlMinutes));
  }

  private Optional<BigDecimal> refreshLive() {
    try {
      return Optional.ofNullable(hybridQuoteService.getUsdMxnRate());
    } catch (RuntimeException ex) {
      log.warn("Refresco en vivo de USD/MXN fallo; se usara el cache vencido si existe", ex);
      return Optional.empty();
    }
  }
}
