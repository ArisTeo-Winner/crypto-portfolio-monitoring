package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.marketdata.application.port.in.SyncDividendsUseCase;
import com.mx.cryptomonitor.marketdata.application.service.DividendRecheckService;
import com.mx.cryptomonitor.marketdata.domain.model.AssetDividendStatusEntity;
import com.mx.cryptomonitor.marketdata.domain.model.DividendEligibilityStatus;
import com.mx.cryptomonitor.marketdata.domain.repository.AssetDividendStatusRepository;

@ExtendWith(MockitoExtension.class)
class DividendRecheckServiceTest {

  @Mock private SyncDividendsUseCase syncDividendsUseCase;
  @Mock private AssetDividendStatusRepository dividendStatusRepository;

  private DividendRecheckService service;

  @BeforeEach
  void setUp() {
    service = new DividendRecheckService(syncDividendsUseCase, dividendStatusRepository);
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "noneTtl", Duration.ofDays(30));
    ReflectionTestUtils.setField(service, "errorTtl", Duration.ofHours(6));
    ReflectionTestUtils.setField(service, "paysTtl", Duration.ofDays(7));
    ReflectionTestUtils.setField(service, "batchSize", 100);
  }

  private AssetDividendStatusEntity row(String symbol, DividendEligibilityStatus status) {
    return AssetDividendStatusEntity.builder()
        .assetSymbol(symbol)
        .status(status)
        .lastCheckedAt(OffsetDateTime.parse("2020-01-01T00:00:00Z"))
        .build();
  }

  @Test
  void resyncsEachDueSymbol() {
    when(dividendStatusRepository.findDueForRecheck(any(), any(), any(), any(Pageable.class)))
        .thenReturn(
            List.of(
                row("GOOGL", DividendEligibilityStatus.PAYS),
                row("KO", DividendEligibilityStatus.NONE)));

    service.recheckDue();

    verify(syncDividendsUseCase).sync("GOOGL");
    verify(syncDividendsUseCase).sync("KO");
  }

  @Test
  void perSymbolFailureDoesNotStopOthers() {
    when(dividendStatusRepository.findDueForRecheck(any(), any(), any(), any(Pageable.class)))
        .thenReturn(
            List.of(
                row("GOOGL", DividendEligibilityStatus.ERROR),
                row("KO", DividendEligibilityStatus.PAYS)));
    when(syncDividendsUseCase.sync("GOOGL")).thenThrow(new RuntimeException("boom"));

    service.recheckDue();

    verify(syncDividendsUseCase).sync("GOOGL");
    verify(syncDividendsUseCase).sync("KO");
  }

  @Test
  void disabledIsNoOp() {
    ReflectionTestUtils.setField(service, "enabled", false);

    service.recheckDue();

    verifyNoInteractions(dividendStatusRepository, syncDividendsUseCase);
  }
}
