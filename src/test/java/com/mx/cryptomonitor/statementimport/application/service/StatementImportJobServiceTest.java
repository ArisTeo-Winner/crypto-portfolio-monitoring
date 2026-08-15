package com.mx.cryptomonitor.statementimport.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportJobResponse;
import com.mx.cryptomonitor.statementimport.domain.exception.StatementImportJobNotFoundException;
import com.mx.cryptomonitor.statementimport.domain.exception.StatementImportJobNotRetryableException;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJob;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobStatus;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;
import com.mx.cryptomonitor.statementimport.domain.repository.StatementImportJobRepository;

@ExtendWith(MockitoExtension.class)
class StatementImportJobServiceTest {

  @Mock private StatementImportJobRepository jobRepository;

  private StatementImportJobService service;

  @BeforeEach
  void setUp() {
    service = new StatementImportJobService(jobRepository, new ObjectMapper());
  }

  @Test
  void retryRequeuesADeadLetterJob() {
    UUID userId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    StatementImportJob job = deadLetterJob(userId, jobId);
    when(jobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));
    when(jobRepository.save(job)).thenReturn(job);

    StatementImportJobResponse response = service.retry(userId, jobId);

    ArgumentCaptor<StatementImportJob> saved = ArgumentCaptor.forClass(StatementImportJob.class);
    verify(jobRepository).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(StatementImportJobStatus.QUEUED);
    assertThat(saved.getValue().getNextAttemptAt()).isNull();
    assertThat(saved.getValue().getCompletedAt()).isNull();
    assertThat(response.status()).isEqualTo("QUEUED");
  }

  @Test
  void retryRejectsAJobThatIsNotDeadLetter() {
    UUID userId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    StatementImportJob job = deadLetterJob(userId, jobId);
    job.setStatus(StatementImportJobStatus.PROCESSING);
    when(jobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service.retry(userId, jobId))
        .isInstanceOf(StatementImportJobNotRetryableException.class);
  }

  @Test
  void retryRejectsAJobThatDoesNotBelongToTheUser() {
    UUID userId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    when(jobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.retry(userId, jobId))
        .isInstanceOf(StatementImportJobNotFoundException.class);
  }

  private StatementImportJob deadLetterJob(UUID userId, UUID jobId) {
    return StatementImportJob.builder()
        .id(jobId)
        .userId(userId)
        .jobType(StatementImportJobType.GBM_STATEMENT)
        .status(StatementImportJobStatus.DEAD_LETTER)
        .fileName("estado.pdf")
        .fileContent(new byte[] {1})
        .attemptCount(3)
        .errorMessage("timeout de red")
        .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
        .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
