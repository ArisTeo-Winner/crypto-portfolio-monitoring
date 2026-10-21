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
import com.mx.cryptomonitor.marketdata.domain.model.MergerActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.MergerType;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.MergerActionDetailRepository;

/** ADR-0014 F6b: valida el detalle MERGER 1:1 (PK compartida vía @MapsId) — De-SPAC CEPT->SECZ. */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MergerActionDetailRepositoryIT {

  @Autowired private CorporateActionRepository corporateActionRepository;
  @Autowired private MergerActionDetailRepository detailRepository;

  @BeforeEach
  void clean() {
    detailRepository.deleteAll();
    corporateActionRepository.deleteAll();
  }

  @Test
  void persistsMergerDetailSharingPrimaryKeyWithTheCorporateAction() {
    // Evento sobre el PREDECESOR (CEPT), fusion all-stock hacia SECZ.
    CorporateActionEntity event =
        corporateActionRepository.saveAndFlush(
            CorporateActionEntity.builder()
                .assetSymbol("CEPT")
                .actionType(CorporateActionType.MERGER)
                .status(CorporateActionStatus.COMPLETED)
                .pivotalDate(LocalDate.of(2024, 3, 1))
                .sourceProvider("SEED_MX")
                .providerEventId("merger:CEPT-SECZ")
                .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

    detailRepository.saveAndFlush(
        MergerActionDetailEntity.builder()
            .corporateAction(event)
            .mergerType(MergerType.ALL_STOCK)
            .successorSymbol("SECZ")
            .exchangeRatio(new BigDecimal("1.00000000"))
            .currency("USD")
            .build());

    MergerActionDetailEntity loaded = detailRepository.findById(event.getId()).orElseThrow();
    assertThat(loaded.getCorporateActionId()).isEqualTo(event.getId());
    assertThat(loaded.getMergerType()).isEqualTo(MergerType.ALL_STOCK);
    assertThat(loaded.getSuccessorSymbol()).isEqualTo("SECZ");
    assertThat(loaded.getExchangeRatio()).isEqualByComparingTo("1");
    assertThat(loaded.getCashPerShare()).isNull();
  }
}
