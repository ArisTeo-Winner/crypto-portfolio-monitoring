package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.integration.support.ContainersConfig;
import com.mx.cryptomonitor.marketdata.domain.model.BmvPriceHistoryEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.BmvPriceHistoryRepository;

/**
 * Verifica el upsert nativo de {@link BmvPriceHistoryRepository} (ON CONFLICT DO NOTHING) contra
 * Postgres real, ya que esa sintaxis no es soportada por H2.
 */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class BmvPriceHistoryRepositoryUpsertIT {

  private static final String SYMBOL = "AMXL*";

  @Autowired private BmvPriceHistoryRepository historyRepository;

  @Test
  void upsertOnConflictDoesNotDuplicateRowForSameSymbolAndDate() {
    LocalDate tradeDate = LocalDate.of(2025, 2, 26);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    historyRepository.upsert(
        UUID.randomUUID(),
        SYMBOL,
        tradeDate,
        new BigDecimal("10.00"),
        new BigDecimal("100.00"),
        "DATABURSATIL",
        now);
    historyRepository.upsert(
        UUID.randomUUID(),
        SYMBOL,
        tradeDate,
        new BigDecimal("99.00"),
        new BigDecimal("999.00"),
        "DATABURSATIL",
        now);

    List<BmvPriceHistoryEntity> rows =
        historyRepository.findByEmisoraSerieAndTradeDateBetween(SYMBOL, tradeDate, tradeDate);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getClosePrice()).isEqualByComparingTo("10.00");
  }
}
