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
import com.mx.cryptomonitor.marketdata.domain.model.TenderOfferActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.TenderOfferActionDetailRepository;

/** ADR-0014 F6c: valida el detalle TENDER_OFFER 1:1 (@MapsId) — caso TRAXION (OPA -> delisting). */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TenderOfferActionDetailRepositoryIT {

  @Autowired private CorporateActionRepository corporateActionRepository;
  @Autowired private TenderOfferActionDetailRepository detailRepository;

  @BeforeEach
  void clean() {
    detailRepository.deleteAll();
    corporateActionRepository.deleteAll();
  }

  @Test
  void persistsTenderOfferDetailSharingPrimaryKeyWithTheCorporateAction() {
    CorporateActionEntity event =
        corporateActionRepository.saveAndFlush(
            CorporateActionEntity.builder()
                .assetSymbol("TRAXIONA")
                .actionType(CorporateActionType.TENDER_OFFER)
                .status(CorporateActionStatus.COMPLETED)
                .pivotalDate(LocalDate.of(2024, 9, 15))
                .sourceProvider("SEED_MX")
                .providerEventId("tender:TRAXIONA-2024")
                .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

    detailRepository.saveAndFlush(
        TenderOfferActionDetailEntity.builder()
            .corporateAction(event)
            .offerPrice(new BigDecimal("28.50000000"))
            .currency("MXN")
            .offerorSymbol(null)
            .resultedInDelisting(true)
            .build());

    TenderOfferActionDetailEntity loaded = detailRepository.findById(event.getId()).orElseThrow();
    assertThat(loaded.getCorporateActionId()).isEqualTo(event.getId());
    assertThat(loaded.getOfferPrice()).isEqualByComparingTo("28.5");
    assertThat(loaded.getCurrency()).isEqualTo("MXN");
    assertThat(loaded.isResultedInDelisting()).isTrue();
  }
}
