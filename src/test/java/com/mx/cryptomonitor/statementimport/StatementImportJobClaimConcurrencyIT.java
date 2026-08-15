package com.mx.cryptomonitor.statementimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;
import com.mx.cryptomonitor.statementimport.application.service.StatementImportJobLifecycleService;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJob;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobStatus;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;
import com.mx.cryptomonitor.statementimport.domain.repository.StatementImportJobRepository;

/**
 * Verifica que FOR UPDATE SKIP LOCKED reparte filas QUEUED sin solaparse entre "instancias"
 * concurrentes del worker, simuladas aqui con dos hilos llamando claimBatch al mismo tiempo.
 * Desactiva el poll del @Scheduled real (poll-interval-ms muy alto) para que no interfiera con el
 * conteo determinista de jobs disponibles.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class StatementImportJobClaimConcurrencyIT extends InfraIntegrationTest {

  private static final int TOTAL_JOBS = 10;
  private static final int JOBS_PER_THREAD = 5;

  @Autowired private StatementImportJobRepository jobRepository;
  @Autowired private StatementImportJobLifecycleService lifecycleService;

  @DynamicPropertySource
  static void disableRealWorkerPolling(DynamicPropertyRegistry registry) {
    registry.add("statementimport.worker.poll-interval-ms", () -> "3600000");
  }

  @Test
  void twoConcurrentClaimersNeverClaimTheSameJob() throws InterruptedException {
    UUID userId = UUID.randomUUID();
    List<UUID> insertedIds =
        IntStream.range(0, TOTAL_JOBS).mapToObj(i -> insertQueuedJob(userId)).toList();

    CountDownLatch startLatch = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var futureA =
          executor.submit(
              () -> {
                awaitLatch(startLatch);
                return lifecycleService.claimBatch(JOBS_PER_THREAD);
              });
      var futureB =
          executor.submit(
              () -> {
                awaitLatch(startLatch);
                return lifecycleService.claimBatch(JOBS_PER_THREAD);
              });

      startLatch.countDown();
      List<StatementImportJob> claimedByA = futureA.get(10, TimeUnit.SECONDS);
      List<StatementImportJob> claimedByB = futureB.get(10, TimeUnit.SECONDS);

      Set<UUID> idsFromA =
          claimedByA.stream().map(StatementImportJob::getId).collect(Collectors.toSet());
      Set<UUID> idsFromB =
          claimedByB.stream().map(StatementImportJob::getId).collect(Collectors.toSet());

      assertThat(idsFromA).doesNotContainAnyElementsOf(idsFromB);
      assertThat(idsFromA).isSubsetOf(insertedIds);
      assertThat(idsFromB).isSubsetOf(insertedIds);
      assertThat(claimedByA)
          .allSatisfy(
              job -> assertThat(job.getStatus()).isEqualTo(StatementImportJobStatus.PROCESSING));
      assertThat(claimedByB)
          .allSatisfy(
              job -> assertThat(job.getStatus()).isEqualTo(StatementImportJobStatus.PROCESSING));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void claimBatchSkipsQueuedJobsWhoseBackoffHasNotExpiredYet() {
    UUID userId = UUID.randomUUID();
    UUID readyJobId = insertQueuedJob(userId);
    UUID backoffJobId =
        insertQueuedJobWithNextAttemptAt(
            userId, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));

    List<StatementImportJob> claimed = lifecycleService.claimBatch(10);

    Set<UUID> claimedIds =
        claimed.stream().map(StatementImportJob::getId).collect(Collectors.toSet());
    assertThat(claimedIds).contains(readyJobId);
    assertThat(claimedIds).doesNotContain(backoffJobId);
  }

  private void awaitLatch(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private UUID insertQueuedJob(UUID userId) {
    return insertQueuedJobWithNextAttemptAt(userId, null);
  }

  private UUID insertQueuedJobWithNextAttemptAt(UUID userId, OffsetDateTime nextAttemptAt) {
    StatementImportJob job =
        StatementImportJob.builder()
            .userId(userId)
            .jobType(StatementImportJobType.GBM_STATEMENT)
            .status(StatementImportJobStatus.QUEUED)
            .fileName("fixture.pdf")
            .fileContent(new byte[] {1, 2, 3})
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .nextAttemptAt(nextAttemptAt)
            .build();
    return jobRepository.save(job).getId();
  }
}
