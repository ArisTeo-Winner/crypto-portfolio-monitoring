package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.mx.cryptomonitor.integration.support.ContainersConfig;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.SplitActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.SplitActionDetailRepository;

/** ADR-0014 F2b: valida el detalle SPLIT 1:1 (PK compartida con corporate_action vía @MapsId). */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SplitActionDetailRepositoryIT {

  @Autowired private CorporateActionRepository corporateActionRepository;
  @Autowired private SplitActionDetailRepository detailRepository;

  @BeforeEach
  void clean() {
    detailRepository.deleteAll();
    corporateActionRepository.deleteAll();
  }

  @Test
  void persistsSplitDetailSharingPrimaryKeyWithTheCorporateAction() {
    CorporateActionEntity event =
        corporateActionRepository.saveAndFlush(
            CorporateActionEntity.builder()
                .assetSymbol("NVDA")
                .actionType(CorporateActionType.SPLIT)
                .status(CorporateActionStatus.COMPLETED)
                .pivotalDate(LocalDate.of(2024, 6, 10))
                .sourceProvider("MASSIVE")
                .providerEventId("split:abc123")
                .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

    detailRepository.saveAndFlush(
        SplitActionDetailEntity.builder()
            .corporateAction(event)
            .shareMultiplier(new BigDecimal("10.0000000000")) // 10:1 forward
            .splitFrom(new BigDecimal("1.000000"))
            .splitTo(new BigDecimal("10.000000"))
            .adjustmentType("forward_split")
            .build());

    SplitActionDetailEntity loaded = detailRepository.findById(event.getId()).orElseThrow();
    assertThat(loaded.getCorporateActionId()).isEqualTo(event.getId());
    assertThat(loaded.getShareMultiplier()).isEqualByComparingTo("10");
    assertThat(loaded.getSplitTo()).isEqualByComparingTo("10");
    assertThat(loaded.getAdjustmentType()).isEqualTo("forward_split");
  }
}
