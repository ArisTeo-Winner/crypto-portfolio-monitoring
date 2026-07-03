package com.mx.cryptomonitor.integration.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;

/** Verifica CatalogRedisAdapter (CatalogStorePort) contra una instancia real de Redis. */
@SpringBootTest
@ActiveProfiles("test")
class CatalogRedisServiceIT extends InfraIntegrationTest {

  @Autowired private CatalogStorePort catalogStorePort;
  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void clearCatalog() {
    Set<String> keys = redisTemplate.keys("catalog:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void saveEntryAndFindEntryRoundTrip() {
    AssetCatalogDto dto =
        new AssetCatalogDto(
            "NVDA",
            "NVIDIA Corporation",
            "STOCK",
            "https://companieslogo.com/api/starter/stock-symbol/NVDA",
            "NASDAQGS",
            "USD",
            3_000_000L);

    catalogStorePort.saveEntry(dto);

    Optional<AssetCatalogDto> found = catalogStorePort.findEntry("NVDA");
    assertThat(found).isPresent();
    assertThat(found.get()).isEqualTo(dto);
  }

  @Test
  void addToRankingOrdersTopSymbolsByScoreDescending() {
    catalogStorePort.addToRanking("catalog:search:stock", "NVDA", 4967d);
    catalogStorePort.addToRanking("catalog:search:stock", "AAPL", 4514d);

    List<String> top = catalogStorePort.getTopSymbols("catalog:search:stock", 10);

    assertThat(top).containsExactly("NVDA", "AAPL");
  }

  @Test
  void findEntryFallsBackToMiscTierWhenNotInMainEntry() {
    AssetCatalogDto dto =
        new AssetCatalogDto("GRNY", "GreenTech Misc", "STOCK", null, null, "USD", 1_000L);
    catalogStorePort.saveMiscEntry(dto, Duration.ofHours(24));

    Optional<AssetCatalogDto> found = catalogStorePort.findEntry("GRNY");

    assertThat(found).isPresent();
    assertThat(found.get().symbol()).isEqualTo("GRNY");
    assertThat(found.get().name()).isEqualTo("GreenTech Misc");
  }

  @Test
  void saveMiscEntryRespectsTtl() {
    AssetCatalogDto dto =
        new AssetCatalogDto("GRNY", "GreenTech Misc", "STOCK", null, null, "USD", 1_000L);

    catalogStorePort.saveMiscEntry(dto, Duration.ofHours(24));

    Long expire = redisTemplate.getExpire("catalog:search:misc:GRNY");
    assertThat(expire).isNotNull().isPositive();
  }

  @Test
  void isCatalogLoadedReflectsStockRankingPresence() {
    assertThat(catalogStorePort.isCatalogLoaded()).isFalse();

    catalogStorePort.addToRanking("catalog:search:stock", "AAPL", 100d);

    assertThat(catalogStorePort.isCatalogLoaded()).isTrue();
  }
}
