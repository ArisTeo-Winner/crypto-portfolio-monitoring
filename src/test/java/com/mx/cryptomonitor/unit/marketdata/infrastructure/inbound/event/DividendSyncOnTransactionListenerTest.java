package com.mx.cryptomonitor.unit.marketdata.infrastructure.inbound.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.in.SyncDividendsUseCase;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.event.DividendSyncOnTransactionListener;
import com.mx.cryptomonitor.shared.event.TransactionRegisteredEvent;

@ExtendWith(MockitoExtension.class)
class DividendSyncOnTransactionListenerTest {

  @Mock private SyncDividendsUseCase syncDividendsUseCase;

  @InjectMocks private DividendSyncOnTransactionListener listener;

  @Test
  void dispatchesSyncForTheRegisteredSymbol() {
    when(syncDividendsUseCase.sync("GOOGL")).thenReturn(8);

    listener.onTransactionRegistered(new TransactionRegisteredEvent("GOOGL", "STOCK"));

    verify(syncDividendsUseCase).sync("GOOGL");
  }

  @Test
  void swallowsProviderFailureSoItNeverBreaksTheRegistration() {
    when(syncDividendsUseCase.sync("GOOGL")).thenThrow(new RuntimeException("provider down"));

    assertThatCode(
            () ->
                listener.onTransactionRegistered(new TransactionRegisteredEvent("GOOGL", "STOCK")))
        .doesNotThrowAnyException();

    verify(syncDividendsUseCase).sync("GOOGL");
  }
}
