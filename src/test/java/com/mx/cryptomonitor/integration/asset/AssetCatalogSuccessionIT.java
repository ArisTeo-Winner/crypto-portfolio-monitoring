package com.mx.cryptomonitor.integration.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.model.MarketStatus;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;
import com.mx.cryptomonitor.integration.support.ContainersConfig;

/**
 * ADR-0014 F6a: valida la sucesion de seguridades en asset_catalog (predecessor_symbol/figi,
 * market_status + default ACTIVE + CHECK) contra Postgres. Caso De-SPAC: CEPT -> SECZ.
 */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AssetCatalogSuccessionIT {

  @Autowired private AssetCatalogRepository repository;

  private static final String PREDECESSOR = "ZZCEPT";
  private static final String SUCCESSOR = "ZZSECZ";
  private static final String INVALID = "ZZBOGUS";

  @AfterEach
  void cleanup() {
    repository.deleteAllById(List.of(PREDECESSOR, SUCCESSOR, INVALID));
  }

  private AssetCatalogEntity.AssetCatalogEntityBuilder base(String symbol) {
    return AssetCatalogEntity.builder()
        .symbol(symbol)
        .assetType("STOCK")
        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void successorPointsToMutedPredecessorAndDefaultsToActive() {
    repository.saveAndFlush(
        base(PREDECESSOR).marketStatus(MarketStatus.MUTED_BY_MERGER.name()).build());
    // Sin fijar market_status -> debe quedar ACTIVE (default Java + DB).
    repository.saveAndFlush(
        base(SUCCESSOR).predecessorSymbol(PREDECESSOR).predecessorFigi("BBG00SECZ001").build());

    AssetCatalogEntity successor = repository.findByPredecessorSymbol(PREDECESSOR).orElseThrow();
    assertThat(successor.getSymbol()).isEqualTo(SUCCESSOR);
    assertThat(successor.getPredecessorFigi()).isEqualTo("BBG00SECZ001");
    assertThat(successor.getMarketStatus()).isEqualTo(MarketStatus.ACTIVE.name());

    assertThat(repository.findByMarketStatus(MarketStatus.MUTED_BY_MERGER.name()))
        .extracting(AssetCatalogEntity::getSymbol)
        .contains(PREDECESSOR);
  }

  @Test
  void rejectsMarketStatusOutsideTheAllowedSet() {
    AssetCatalogEntity bogus = base(INVALID).marketStatus("NOT_A_STATUS").build();

    assertThatThrownBy(() -> repository.saveAndFlush(bogus))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
