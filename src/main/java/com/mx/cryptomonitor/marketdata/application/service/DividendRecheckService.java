package com.mx.cryptomonitor.marketdata.application.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.in.SyncDividendsUseCase;
import com.mx.cryptomonitor.marketdata.domain.model.AssetDividendStatusEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.AssetDividendStatusRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Re-chequeo programado de la elegibilidad de dividendos (ADR-0025 F4-B). Mantiene fresco el
 * estado: un {@code NONE} que empezó a pagar pasa a {@code PAYS}, un {@code ERROR} (no concluyente)
 * se reintenta, y un {@code PAYS} se refresca para descubrir pagos nuevos (que `sync` acredita a
 * todos los tenedores vía {@code DividendDetectedEvent}). Cada estado tiene su propia cadencia; el
 * barrido se acota por {@code batch-size} (batching, ADR-0028 F5) para no escalar de golpe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DividendRecheckService {

  private final SyncDividendsUseCase syncDividendsUseCase;
  private final AssetDividendStatusRepository dividendStatusRepository;

  @Value("${dividends.recheck.enabled:true}")
  private boolean enabled;

  @Value("${dividends.recheck.none-ttl:P30D}")
  private Duration noneTtl;

  @Value("${dividends.recheck.error-ttl:PT6H}")
  private Duration errorTtl;

  @Value("${dividends.recheck.pays-ttl:P7D}")
  private Duration paysTtl;

  @Value("${dividends.recheck.batch-size:100}")
  private int batchSize;

  @Scheduled(cron = "${dividends.recheck.cron:0 0 4 * * *}")
  public void recheckDue() {
    if (!enabled) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<AssetDividendStatusEntity> due =
        dividendStatusRepository.findDueForRecheck(
            now.minus(noneTtl),
            now.minus(errorTtl),
            now.minus(paysTtl),
            PageRequest.of(0, batchSize));
    if (due.isEmpty()) {
      return;
    }
    int resynced = 0;
    for (AssetDividendStatusEntity status : due) {
      try {
        syncDividendsUseCase.sync(status.getAssetSymbol());
        resynced++;
      } catch (RuntimeException ex) {
        log.warn(
            "Re-chequeo de dividendos falló para {}: {}", status.getAssetSymbol(), ex.getMessage());
      }
    }
    log.info("Re-chequeo de dividendos: {}/{} símbolo(s) re-sincronizado(s)", resynced, due.size());
  }
}
