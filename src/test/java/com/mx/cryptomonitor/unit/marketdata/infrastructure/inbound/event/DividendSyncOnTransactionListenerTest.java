package com.mx.cryptomonitor.unit.marketdata.infrastructure.inbound.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.service.DividendSyncJobLifecycleService;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.event.DividendSyncOnTransactionListener;
import com.mx.cryptomonitor.shared.event.TransactionRegisteredEvent;

@ExtendWith(MockitoExtension.class)
class DividendSyncOnTransactionListenerTest {

  @Mock private DividendSyncJobLifecycleService jobLifecycleService;

  @InjectMocks private DividendSyncOnTransactionListener listener;

  @Test
  void enqueuesTheRegisteredSymbolForDurableProcessing() {
    listener.onTransactionRegistered(new TransactionRegisteredEvent("GOOGL", "STOCK"));

    // ADR-0028 F2: solo ENCOLA; el worker durable hace el sync + replay.
    verify(jobLifecycleService).enqueue("GOOGL");
  }

  @Test
  void swallowsEnqueueFailureSoItNeverBreaksTheRegistration() {
    doThrow(new RuntimeException("db down")).when(jobLifecycleService).enqueue("GOOGL");

    assertThatCode(
            () ->
                listener.onTransactionRegistered(new TransactionRegisteredEvent("GOOGL", "STOCK")))
        .doesNotThrowAnyException();

    verify(jobLifecycleService).enqueue("GOOGL");
  }
}
