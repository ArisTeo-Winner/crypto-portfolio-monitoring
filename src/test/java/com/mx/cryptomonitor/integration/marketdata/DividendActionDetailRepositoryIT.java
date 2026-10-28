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
import com.mx.cryptomonitor.marketdata.domain.model.DividendActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.DividendDistributionType;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.DividendActionDetailRepository;

/**
 * ADR-0014 F3a: valida el detalle DIVIDEND 1:1 (PK compartida con corporate_action via @MapsId).
 */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DividendActionDetailRepositoryIT {

  @Autowired private CorporateActionRepository corporateActionRepository;
  @Autowired private DividendActionDetailRepository detailRepository;

  @BeforeEach
  void clean() {
    detailRepository.deleteAll();
    corporateActionRepository.deleteAll();
  }

  @Test
  void persistsDetailSharingPrimaryKeyWithTheCorporateAction() {
    CorporateActionEntity event =
        corporateActionRepository.saveAndFlush(
            CorporateActionEntity.builder()
                .assetSymbol("GOOGL")
                .actionType(CorporateActionType.DIVIDEND)
                .status(CorporateActionStatus.COMPLETED)
                .pivotalDate(LocalDate.of(2024, 6, 10))
                .paymentDate(LocalDate.of(2024, 6, 17))
                .sourceProvider("MASSIVE")
                .providerEventId("E1cdc29fc58066")
                .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

    DividendActionDetailEntity detail =
        DividendActionDetailEntity.builder()
            .corporateAction(event)
            .amountPerShare(new BigDecimal("0.20000000"))
            .currency("USD")
            .frequency((short) 4)
            .distributionType(DividendDistributionType.RECURRING)
            .splitAdjustedDps(new BigDecimal("0.20000000"))
            .priceAdjustmentFactor(new BigDecimal("0.99416500"))
            .build();

    detailRepository.saveAndFlush(detail);

    DividendActionDetailEntity loaded = detailRepository.findById(event.getId()).orElseThrow();
    // @MapsId: el detalle comparte la PK del evento.
    assertThat(loaded.getCorporateActionId()).isEqualTo(event.getId());
    assertThat(loaded.getAmountPerShare()).isEqualByComparingTo("0.20");
    assertThat(loaded.getCurrency()).isEqualTo("USD");
    assertThat(loaded.getFrequency()).isEqualTo((short) 4);
    assertThat(loaded.getDistributionType()).isEqualTo(DividendDistributionType.RECURRING);
    assertThat(loaded.getPriceAdjustmentFactor()).isEqualByComparingTo("0.994165");
  }
}
