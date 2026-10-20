package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;

/** ADR-0014 F2a: valida el mapeo de la base corporate_action (enum, fechas, JSONB, dedup). */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CorporateActionRepositoryIT {

  @Autowired private CorporateActionRepository repository;

  @BeforeEach
  void clean() {
    repository.deleteAll();
  }

  @Test
  void persistsAndReadsBackWithEnumsDatesAndJsonbPayload() {
    CorporateActionEntity dividend =
        CorporateActionEntity.builder()
            .assetSymbol("GOOGL")
            .actionType(CorporateActionType.DIVIDEND)
            .status(CorporateActionStatus.COMPLETED)
            .pivotalDate(LocalDate.of(2024, 6, 10)) // ex-date
            .declarationDate(LocalDate.of(2024, 4, 25))
            .recordDate(LocalDate.of(2024, 6, 10))
            .paymentDate(LocalDate.of(2024, 6, 17))
            .sourceProvider("MASSIVE")
            .providerEventId("E1cdc29fc58066")
            .rawPayload("{\"cash_amount\":0.20,\"currency\":\"USD\",\"frequency\":4}")
            .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

    repository.saveAndFlush(dividend);

    assertThat(repository.findByAssetSymbolOrderByPivotalDateDesc("GOOGL"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getActionType()).isEqualTo(CorporateActionType.DIVIDEND);
              assertThat(e.getStatus()).isEqualTo(CorporateActionStatus.COMPLETED);
              assertThat(e.getPivotalDate()).isEqualTo(LocalDate.of(2024, 6, 10));
              assertThat(e.getPaymentDate()).isEqualTo(LocalDate.of(2024, 6, 17));
              assertThat(e.getRawPayload()).contains("\"cash_amount\"");
            });

    assertThat(repository.findByProviderEventId("E1cdc29fc58066")).isPresent();
    assertThat(
            repository.existsByAssetSymbolAndActionTypeAndPivotalDate(
                "GOOGL", CorporateActionType.DIVIDEND, LocalDate.of(2024, 6, 10)))
        .isTrue();
  }

  @Test
  void timelineOrdersByPivotalDateDescending() {
    repository.saveAll(
        java.util.List.of(
            newSplit("WETO", LocalDate.of(2026, 8, 3)),
            newSplit("WETO", LocalDate.of(2024, 1, 15))));

    assertThat(repository.findByAssetSymbolOrderByPivotalDateDesc("WETO"))
        .extracting(CorporateActionEntity::getPivotalDate)
        .containsExactly(LocalDate.of(2026, 8, 3), LocalDate.of(2024, 1, 15));
  }

  private CorporateActionEntity newSplit(String symbol, LocalDate date) {
    return CorporateActionEntity.builder()
        .assetSymbol(symbol)
        .actionType(CorporateActionType.SPLIT)
        .status(CorporateActionStatus.COMPLETED)
        .pivotalDate(date)
        .sourceProvider("MASSIVE")
        .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
