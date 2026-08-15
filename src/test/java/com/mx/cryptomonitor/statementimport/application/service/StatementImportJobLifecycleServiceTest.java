package com.mx.cryptomonitor.statementimport.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJob;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobStatus;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;
import com.mx.cryptomonitor.statementimport.domain.repository.StatementImportJobRepository;

@ExtendWith(MockitoExtension.class)
class StatementImportJobLifecycleServiceTest {

  private static final int MAX_ATTEMPTS = 3;

  @Mock private StatementImportJobRepository jobRepository;

  private StatementImportJobLifecycleService service;

  @BeforeEach
  void setUp() {
    service = new StatementImportJobLifecycleService(jobRepository, new ObjectMapper());
    ReflectionTestUtils.setField(service, "maxAttempts", MAX_ATTEMPTS);
  }

  @Test
  void permanentErrorGoesStraightToDeadLetterOnFirstAttempt() {
    StatementImportJob job = queuedJob();
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    service.markFailed(job.getId(), "PDF no reconocido", true);

    StatementImportJob updated = captureSaved();
    assertThat(updated.getStatus()).isEqualTo(StatementImportJobStatus.DEAD_LETTER);
    assertThat(updated.getAttemptCount()).isEqualTo(1);
    assertThat(updated.getErrorMessage()).isEqualTo("PDF no reconocido");
    assertThat(updated.getCompletedAt()).isNotNull();
    assertThat(updated.getNextAttemptAt()).isNull();
  }

  @Test
  void transientErrorRequeuesWithBackoffWhileAttemptsRemain() {
    StatementImportJob job = queuedJob();
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

    service.markFailed(job.getId(), "timeout de red", false);

    StatementImportJob updated = captureSaved();
    assertThat(updated.getStatus()).isEqualTo(StatementImportJobStatus.QUEUED);
    assertThat(updated.getAttemptCount()).isEqualTo(1);
    assertThat(updated.getCompletedAt()).isNull();
    assertThat(updated.getNextAttemptAt()).isAfter(before);
  }

  @Test
  void transientErrorGoesToDeadLetterOnceMaxAttemptsReached() {
    StatementImportJob job = queuedJob();
    job.setAttemptCount(MAX_ATTEMPTS - 1);
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    service.markFailed(job.getId(), "timeout de red", false);

    StatementImportJob updated = captureSaved();
    assertThat(updated.getStatus()).isEqualTo(StatementImportJobStatus.DEAD_LETTER);
    assertThat(updated.getAttemptCount()).isEqualTo(MAX_ATTEMPTS);
    assertThat(updated.getNextAttemptAt()).isNull();
  }

  @Test
  void laterAttemptsGetLongerBackoffThanEarlierOnes() {
    StatementImportJob firstAttemptJob = queuedJob();
    when(jobRepository.findById(firstAttemptJob.getId())).thenReturn(Optional.of(firstAttemptJob));
    service.markFailed(firstAttemptJob.getId(), "timeout", false);
    Duration firstDelay = delayUntilNextAttempt(captureSaved());

    StatementImportJob secondAttemptJob = queuedJob();
    secondAttemptJob.setAttemptCount(1);
    when(jobRepository.findById(secondAttemptJob.getId()))
        .thenReturn(Optional.of(secondAttemptJob));
    service.markFailed(secondAttemptJob.getId(), "timeout", false);
    Duration secondDelay = delayUntilNextAttempt(captureSaved(2));

    assertThat(secondDelay).isGreaterThan(firstDelay);
  }

  @Test
  void unknownJobIdIsSilentlyIgnored() {
    UUID jobId = UUID.randomUUID();
    when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

    service.markFailed(jobId, "error", true);

    verify(jobRepository, never()).save(any());
  }

  private Duration delayUntilNextAttempt(StatementImportJob job) {
    return Duration.between(OffsetDateTime.now(ZoneOffset.UTC), job.getNextAttemptAt());
  }

  private StatementImportJob captureSaved() {
    return captureSaved(1);
  }

  private StatementImportJob captureSaved(int expectedInvocations) {
    ArgumentCaptor<StatementImportJob> captor = ArgumentCaptor.forClass(StatementImportJob.class);
    verify(jobRepository, times(expectedInvocations)).save(captor.capture());
    return captor.getValue();
  }

  private StatementImportJob queuedJob() {
    return StatementImportJob.builder()
        .id(UUID.randomUUID())
        .userId(UUID.randomUUID())
        .jobType(StatementImportJobType.GBM_STATEMENT)
        .status(StatementImportJobStatus.PROCESSING)
        .fileName("estado.pdf")
        .fileContent(new byte[] {1})
        .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
