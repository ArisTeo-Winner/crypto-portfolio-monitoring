package com.mx.cryptomonitor.unit.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.mx.cryptomonitor.shared.infrastructure.config.AsyncConfig;

class AsyncConfigTest {

  /**
   * ADR-0028 F1: el accrual de dividendos NUNCA se descarta — backpressure con CallerRunsPolicy.
   */
  @Test
  void dividendProcessingExecutorNeverDiscardsTasks() {
    ThreadPoolTaskExecutor executor =
        (ThreadPoolTaskExecutor) new AsyncConfig().dividendProcessingExecutor();
    try {
      assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
          .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    } finally {
      executor.shutdown();
    }
  }

  /** El de íconos sí es best-effort (descarta bajo saturación): no debe cambiarse por error. */
  @Test
  void assetIconExecutorIsBestEffortDiscard() {
    ThreadPoolTaskExecutor executor =
        (ThreadPoolTaskExecutor) new AsyncConfig().assetIconExecutor();
    try {
      assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
          .isInstanceOf(ThreadPoolExecutor.DiscardPolicy.class);
    } finally {
      executor.shutdown();
    }
  }
}
