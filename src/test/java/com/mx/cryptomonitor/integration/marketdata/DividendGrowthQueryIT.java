package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
 * ADR-0014 F5: valida en vivo la query {@code findRecurringDpsBySymbol} (enum FQN + COALESCE +
 * join @MapsId) contra Postgres — solo recurring, por símbolo, prefiriendo split_adjusted_dps.
 */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DividendGrowthQueryIT {

  @Autowired private CorporateActionRepository corporateActionRepository;
  @Autowired private DividendActionDetailRepository detailRepository;

  @BeforeEach
  void clean() {
    detailRepository.deleteAll();
    corporateActionRepository.deleteAll();
  }

  private void dividend(
      String symbol,
      LocalDate exDate,
      String amount,
      String splitAdjusted,
      DividendDistributionType type) {
    CorporateActionEntity event =
        corporateActionRepository.saveAndFlush(
            CorporateActionEntity.builder()
                .assetSymbol(symbol)
                .actionType(CorporateActionType.DIVIDEND)
                .status(CorporateActionStatus.COMPLETED)
                .pivotalDate(exDate)
                .sourceProvider("MASSIVE")
                .providerEventId(symbol + exDate)
                .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    detailRepository.saveAndFlush(
        DividendActionDetailEntity.builder()
            .corporateAction(event)
            .amountPerShare(new BigDecimal(amount))
            .currency("USD")
            .frequency((short) 4)
            .distributionType(type)
            .splitAdjustedDps(splitAdjusted == null ? null : new BigDecimal(splitAdjusted))
            .build());
  }

  @Test
  void returnsOnlyRecurringForTheSymbolPreferringSplitAdjustedDps() {
    dividend("KO", LocalDate.of(2023, 3, 10), "0.46", "0.46", DividendDistributionType.RECURRING);
    // split_adjusted_dps null -> COALESCE cae a amount_per_share.
    dividend("KO", LocalDate.of(2023, 6, 10), "0.46", null, DividendDistributionType.RECURRING);
    // SPECIAL -> excluido.
    dividend("KO", LocalDate.of(2023, 9, 10), "5.00", "5.00", DividendDistributionType.SPECIAL);
    // Otro símbolo -> excluido.
    dividend("PEP", LocalDate.of(2023, 3, 10), "1.26", "1.26", DividendDistributionType.RECURRING);

    List<Object[]> rows = detailRepository.findRecurringDpsBySymbol("KO");

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat(row[0]).isInstanceOf(LocalDate.class);
              assertThat((BigDecimal) row[1]).isEqualByComparingTo("0.46");
            });
  }
}
