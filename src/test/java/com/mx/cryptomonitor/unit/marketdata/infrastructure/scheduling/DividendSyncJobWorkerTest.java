package com.mx.cryptomonitor.unit.marketdata.infrastructure.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.marketdata.application.port.in.ReplayDividendAccrualUseCase;
import com.mx.cryptomonitor.marketdata.application.port.in.SyncDividendsUseCase;
import com.mx.cryptomonitor.marketdata.application.service.DividendSyncJobLifecycleService;
import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJob;
import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJobStatus;
import com.mx.cryptomonitor.marketdata.infrastructure.scheduling.DividendSyncJobWorker;

@ExtendWith(MockitoExtension.class)
class DividendSyncJobWorkerTest {

  @Mock private DividendSyncJobLifecycleService lifecycleService;
  @Mock private SyncDividendsUseCase syncDividendsUseCase;
  @Mock private ReplayDividendAccrualUseCase replayDividendAccrualUseCase;

  private DividendSyncJobWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new DividendSyncJobWorker(
            lifecycleService, syncDividendsUseCase, replayDividendAccrualUseCase);
    ReflectionTestUtils.setField(worker, "enabled", true);
    ReflectionTestUtils.setField(worker, "batchSize", 10);
  }

  private DividendSyncJob job(UUID id, String symbol) {
    return DividendSyncJob.builder()
        .id(id)
        .assetSymbol(symbol)
        .status(DividendSyncJobStatus.PROCESSING)
        .createdAt(OffsetDateTime.now())
        .build();
  }

  @Test
  void processesClaimedJobsWithSyncReplayAndMarksCompleted() {
    UUID id = UUID.randomUUID();
    when(lifecycleService.claimBatch(10)).thenReturn(List.of(job(id, "GOOGL")));

    worker.processQueuedJobs();

    verify(syncDividendsUseCase).sync("GOOGL");
    verify(replayDividendAccrualUseCase).replayForAccrual("GOOGL");
    verify(lifecycleService).markCompleted(id);
  }

  @Test
  void marksFailedOnException() {
    UUID id = UUID.randomUUID();
    when(lifecycleService.claimBatch(10)).thenReturn(List.of(job(id, "GOOGL")));
    when(syncDividendsUseCase.sync("GOOGL")).thenThrow(new RuntimeException("boom"));

    worker.processQueuedJobs();

    verify(lifecycleService).markFailed(eq(id), any());
    verify(lifecycleService, never()).markCompleted(id);
  }

  @Test
  void disabledDoesNotClaim() {
    ReflectionTestUtils.setField(worker, "enabled", false);

    worker.processQueuedJobs();

    verifyNoInteractions(lifecycleService, syncDividendsUseCase, replayDividendAccrualUseCase);
  }
}
