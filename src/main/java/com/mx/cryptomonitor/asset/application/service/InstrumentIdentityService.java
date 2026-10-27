package com.mx.cryptomonitor.asset.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.asset.application.port.out.InstrumentIdentityPort;
import com.mx.cryptomonitor.asset.application.port.out.InstrumentIdentityPort.InstrumentIdentity;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Backfill de identidad canonica (ADR-0019 Fase 1): puebla {@code composite_figi}/{@code
 * share_class_figi} en el catalogo via OpenFIGI. Solo STOCK/ETF (cripto y bonos MX no tienen FIGI).
 * Idempotente: solo toca filas con {@code share_class_figi} NULL; el FIGI es inmutable, no se
 * re-resuelve una vez fijado.
 */
@Service
@RequiredArgsConstructor
public class InstrumentIdentityService {

  private static final Logger log = LoggerFactory.getLogger(InstrumentIdentityService.class);
  private static final List<String> FIGI_ELIGIBLE_TYPES = List.of("STOCK", "ETF");

  private final InstrumentIdentityPort instrumentIdentityPort;
  private final AssetCatalogRepository catalogRepository;

  @Value("${openfigi.default-exch-code:US}")
  private String defaultExchCode;

  @Value("${openfigi.rate-limit.delay-ms:2500}")
  private long rateLimitDelayMs;

  @Scheduled(cron = "${openfigi.backfill-cron:0 30 6 * * MON}")
  public void scheduledBackfill() {
    backfillMissingIdentities();
  }

  /**
   * Resuelve el FIGI de las filas STOCK/ETF que aun no lo tienen y lo persiste. Devuelve cuantas se
   * poblaron.
   */
  public int backfillMissingIdentities() {
    List<AssetCatalogEntity> pending =
        catalogRepository.findByAssetTypeInAndShareClassFigiIsNull(FIGI_ELIGIBLE_TYPES);
    if (pending.isEmpty()) {
      return 0;
    }

    int resolved = 0;
    for (AssetCatalogEntity entity : pending) {
      Optional<InstrumentIdentity> identity =
          instrumentIdentityPort.resolveByTicker(entity.getSymbol(), defaultExchCode);
      throttle();
      if (identity.isEmpty()) {
        continue;
      }
      InstrumentIdentity id = identity.get();
      if (isBlank(id.shareClassFigi()) && isBlank(id.compositeFigi())) {
        continue;
      }
      entity.setCompositeFigi(id.compositeFigi());
      entity.setShareClassFigi(id.shareClassFigi());
      entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      catalogRepository.save(entity);
      resolved++;
    }

    log.info(
        "OpenFIGI backfill: {} de {} simbolos STOCK/ETF con FIGI resuelto",
        resolved,
        pending.size());
    return resolved;
  }

  private void throttle() {
    if (rateLimitDelayMs <= 0) {
      return;
    }
    try {
      Thread.sleep(rateLimitDelayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
