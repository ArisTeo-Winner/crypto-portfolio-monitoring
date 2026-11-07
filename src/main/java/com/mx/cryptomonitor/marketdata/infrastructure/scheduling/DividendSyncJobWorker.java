package com.mx.cryptomonitor.marketdata.infrastructure.scheduling;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.in.ReplayDividendAccrualUseCase;
import com.mx.cryptomonitor.marketdata.application.port.in.SyncDividendsUseCase;
import com.mx.cryptomonitor.marketdata.application.service.DividendSyncJobLifecycleService;
import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker de la cola durable de dividendos (ADR-0028 F2): reclama jobs QUEUED (Postgres-as-queue vía
 * FOR UPDATE SKIP LOCKED) y los procesa — {@code sync} (pobla el catálogo + acredita pagos nuevos a
 * todos los tenedores) y {@code replayForAccrual} (acredita al late joiner, ADR-0027). A diferencia
 * del procesamiento en memoria (F1), esto sobrevive reinicios y reintenta con backoff. Gateado por
 * {@code dividends.sync-worker.enabled} (default true, off en tests).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendSyncJobWorker {

  private final DividendSyncJobLifecycleService lifecycleService;
  private final SyncDividendsUseCase syncDividendsUseCase;
  private final ReplayDividendAccrualUseCase replayDividendAccrualUseCase;

  @Value("${dividends.sync-worker.enabled:true}")
  private boolean enabled;

  @Value("${dividends.sync-worker.batch-size:10}")
  private int batchSize;

  @Scheduled(fixedDelayString = "${dividends.sync-worker.poll-interval-ms:2000}")
  public void processQueuedJobs() {
    if (!enabled) {
      return;
    }
    List<DividendSyncJob> claimed = lifecycleService.claimBatch(batchSize);
    claimed.forEach(this::processJob);
  }

  private void processJob(DividendSyncJob job) {
    try {
      syncDividendsUseCase.sync(job.getAssetSymbol());
      replayDividendAccrualUseCase.replayForAccrual(job.getAssetSymbol());
      lifecycleService.markCompleted(job.getId());
    } catch (RuntimeException ex) {
      log.warn(
          "Fallo al procesar dividend sync job {} ({})", job.getId(), job.getAssetSymbol(), ex);
      lifecycleService.markFailed(job.getId(), ex.getMessage());
    }
  }
}
