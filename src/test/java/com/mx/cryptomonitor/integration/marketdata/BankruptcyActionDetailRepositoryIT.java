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
import com.mx.cryptomonitor.marketdata.domain.model.BankruptcyActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.repository.BankruptcyActionDetailRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;

/** ADR-0014 F6d: valida el detalle BANKRUPTCY 1:1 (@MapsId); recovery 0 = write-off total. */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BankruptcyActionDetailRepositoryIT {

  @Autowired private CorporateActionRepository corporateActionRepository;
  @Autowired private BankruptcyActionDetailRepository detailRepository;

  @BeforeEach
  void clean() {
    detailRepository.deleteAll();
    corporateActionRepository.deleteAll();
  }

  @Test
  void persistsBankruptcyDetailSharingPrimaryKeyWithTheCorporateAction() {
    CorporateActionEntity event =
        corporateActionRepository.saveAndFlush(
            CorporateActionEntity.builder()
                .assetSymbol("DEFUNCT")
                .actionType(CorporateActionType.BANKRUPTCY)
                .status(CorporateActionStatus.COMPLETED)
                .pivotalDate(LocalDate.of(2024, 5, 20))
                .sourceProvider("SEED")
                .providerEventId("bankruptcy:DEFUNCT-2024")
                .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

    detailRepository.saveAndFlush(
        BankruptcyActionDetailEntity.builder()
            .corporateAction(event)
            .recoveryPerShare(new BigDecimal("0.00000000"))
            .currency("USD")
            .build());

    BankruptcyActionDetailEntity loaded = detailRepository.findById(event.getId()).orElseThrow();
    assertThat(loaded.getCorporateActionId()).isEqualTo(event.getId());
    assertThat(loaded.getRecoveryPerShare()).isEqualByComparingTo("0");
    assertThat(loaded.getCurrency()).isEqualTo("USD");
  }
}
