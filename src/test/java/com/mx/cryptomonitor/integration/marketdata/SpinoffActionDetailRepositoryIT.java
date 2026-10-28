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
import com.mx.cryptomonitor.marketdata.domain.model.SpinoffActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.SpinoffActionDetailRepository;

/** ADR-0014 F6e: valida el detalle SPINOFF 1:1 (@MapsId) — matriz reparte spinco + base. */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SpinoffActionDetailRepositoryIT {

  @Autowired private CorporateActionRepository corporateActionRepository;
  @Autowired private SpinoffActionDetailRepository detailRepository;

  @BeforeEach
  void clean() {
    detailRepository.deleteAll();
    corporateActionRepository.deleteAll();
  }

  @Test
  void persistsSpinoffDetailSharingPrimaryKeyWithTheCorporateAction() {
    CorporateActionEntity event =
        corporateActionRepository.saveAndFlush(
            CorporateActionEntity.builder()
                .assetSymbol("PARENT")
                .actionType(CorporateActionType.SPINOFF)
                .status(CorporateActionStatus.COMPLETED)
                .pivotalDate(LocalDate.of(2024, 7, 1))
                .sourceProvider("SEED")
                .providerEventId("spinoff:PARENT-SPINCO")
                .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

    detailRepository.saveAndFlush(
        SpinoffActionDetailEntity.builder()
            .corporateAction(event)
            .spincoSymbol("SPINCO")
            .sharesPerParentShare(new BigDecimal("0.50000000")) // 1 spinco por cada 2 matriz
            .costBasisAllocationPct(new BigDecimal("0.20000000")) // 20% de la base migra
            .currency("USD")
            .build());

    SpinoffActionDetailEntity loaded = detailRepository.findById(event.getId()).orElseThrow();
    assertThat(loaded.getCorporateActionId()).isEqualTo(event.getId());
    assertThat(loaded.getSpincoSymbol()).isEqualTo("SPINCO");
    assertThat(loaded.getSharesPerParentShare()).isEqualByComparingTo("0.5");
    assertThat(loaded.getCostBasisAllocationPct()).isEqualByComparingTo("0.2");
  }
}
