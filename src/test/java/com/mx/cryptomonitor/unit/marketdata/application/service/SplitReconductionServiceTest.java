package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.service.SplitReconductionService;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.SplitActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.SplitActionDetailRepository;

@ExtendWith(MockitoExtension.class)
class SplitReconductionServiceTest {

  @Mock private CorporateActionRepository corporateActionRepository;
  @Mock private SplitActionDetailRepository splitDetailRepository;

  private SplitReconductionService service() {
    return new SplitReconductionService(corporateActionRepository, splitDetailRepository);
  }

  @Test
  void reconductsNewSplitAsCompletedWithDetail() {
    StockSplitData split =
        new StockSplitData("NVDA", LocalDate.of(2024, 6, 10), new BigDecimal("10"));
    when(corporateActionRepository.existsByAssetSymbolAndActionTypeAndPivotalDate(
            "NVDA", CorporateActionType.SPLIT, LocalDate.of(2024, 6, 10)))
        .thenReturn(false);
    when(corporateActionRepository.save(any(CorporateActionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    int created = service().reconduct("nvda", List.of(split));

    assertThat(created).isEqualTo(1);

    ArgumentCaptor<CorporateActionEntity> caCaptor =
        ArgumentCaptor.forClass(CorporateActionEntity.class);
    verify(corporateActionRepository).save(caCaptor.capture());
    assertThat(caCaptor.getValue().getAssetSymbol()).isEqualTo("NVDA");
    assertThat(caCaptor.getValue().getActionType()).isEqualTo(CorporateActionType.SPLIT);
    assertThat(caCaptor.getValue().getStatus()).isEqualTo(CorporateActionStatus.COMPLETED);

    ArgumentCaptor<SplitActionDetailEntity> detailCaptor =
        ArgumentCaptor.forClass(SplitActionDetailEntity.class);
    verify(splitDetailRepository).save(detailCaptor.capture());
    assertThat(detailCaptor.getValue().getShareMultiplier()).isEqualByComparingTo("10");
  }

  @Test
  void skipsSplitAlreadyInBaseByNaturalKey() {
    StockSplitData split =
        new StockSplitData("NVDA", LocalDate.of(2024, 6, 10), new BigDecimal("10"));
    when(corporateActionRepository.existsByAssetSymbolAndActionTypeAndPivotalDate(
            "NVDA", CorporateActionType.SPLIT, LocalDate.of(2024, 6, 10)))
        .thenReturn(true);

    int created = service().reconduct("NVDA", List.of(split));

    assertThat(created).isZero();
    verify(corporateActionRepository, never()).save(any());
    verify(splitDetailRepository, never()).save(any());
  }

  @Test
  void emptyOrBlankIsNoOp() {
    assertThat(service().reconduct("  ", List.of())).isZero();
    assertThat(service().reconduct("NVDA", List.of())).isZero();
    verifyNoInteractions(corporateActionRepository, splitDetailRepository);
  }
}
