package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.marketdata.application.service.DividendSyncJobLifecycleService;
import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJob;
import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJobStatus;
import com.mx.cryptomonitor.marketdata.domain.repository.DividendSyncJobRepository;

@ExtendWith(MockitoExtension.class)
class DividendSyncJobLifecycleServiceTest {

  @Mock private DividendSyncJobRepository jobRepository;

  private DividendSyncJobLifecycleService service;

  @BeforeEach
  void setUp() {
    service = new DividendSyncJobLifecycleService(jobRepository);
    ReflectionTestUtils.setField(service, "maxAttempts", 5);
  }

  private DividendSyncJob processing(UUID id, int attempts) {
    return DividendSyncJob.builder()
        .id(id)
        .assetSymbol("GOOGL")
        .status(DividendSyncJobStatus.PROCESSING)
        .attemptCount(attempts)
        .createdAt(OffsetDateTime.now())
        .build();
  }

  @Test
  void enqueuesWhenNoActiveJob() {
    when(jobRepository.existsByAssetSymbolAndStatusIn(eq("GOOGL"), any())).thenReturn(false);

    service.enqueue("googl");

    ArgumentCaptor<DividendSyncJob> captor = ArgumentCaptor.forClass(DividendSyncJob.class);
    verify(jobRepository).save(captor.capture());
    assertThat(captor.getValue().getAssetSymbol()).isEqualTo("GOOGL");
    assertThat(captor.getValue().getStatus()).isEqualTo(DividendSyncJobStatus.QUEUED);
  }

  @Test
  void skipsEnqueueWhenActiveJobExists() {
    when(jobRepository.existsByAssetSymbolAndStatusIn(eq("GOOGL"), any())).thenReturn(true);

    service.enqueue("GOOGL");

    verify(jobRepository, never()).save(any());
  }

  /** ADR-0028 F4: si el índice único rechaza el insert (carrera), se trata como "ya encolado". */
  @Test
  void enqueueSwallowsRaceConflict() {
    when(jobRepository.existsByAssetSymbolAndStatusIn(eq("GOOGL"), any())).thenReturn(false);
    when(jobRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatCode(() -> service.enqueue("GOOGL")).doesNotThrowAnyException();
  }

  @Test
  void markFailedRequeuesWithBackoffBelowMax() {
    UUID id = UUID.randomUUID();
    DividendSyncJob job = processing(id, 0);
    when(jobRepository.findById(id)).thenReturn(Optional.of(job));

    service.markFailed(id, "boom");

    assertThat(job.getStatus()).isEqualTo(DividendSyncJobStatus.QUEUED);
    assertThat(job.getAttemptCount()).isEqualTo(1);
    assertThat(job.getNextAttemptAt()).isNotNull();
  }

  @Test
  void markFailedDeadLettersAtMaxAttempts() {
    UUID id = UUID.randomUUID();
    DividendSyncJob job = processing(id, 4); // +1 = 5 = max
    when(jobRepository.findById(id)).thenReturn(Optional.of(job));

    service.markFailed(id, "boom");

    assertThat(job.getStatus()).isEqualTo(DividendSyncJobStatus.DEAD_LETTER);
    assertThat(job.getNextAttemptAt()).isNull();
  }
}
