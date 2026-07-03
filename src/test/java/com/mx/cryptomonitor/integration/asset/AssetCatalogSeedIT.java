package com.mx.cryptomonitor.integration.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;
import com.redis.testcontainers.RedisContainer;

/**
 * Verifica que la semilla Flyway ({@code V2026_06_14_04__seed_asset_catalog.sql}) puebla
 * correctamente la tabla {@code asset_catalog}.
 *
 * <p>No extiende {@link com.mx.cryptomonitor.integration.support.InfraIntegrationTest} a proposito:
 * esa clase base fuerza {@code spring.flyway.enabled=false} vía su propio
 * {@code @DynamicPropertySource}, y un {@code @DynamicPropertySource} de subclase no tiene
 * garantizado ejecutarse despues del de la superclase (se comprobo empiricamente que el valor de la
 * superclase gana). Este test necesita que Flyway corra de verdad, asi que declara su propio
 * contenedor Postgres y su unico {@code @DynamicPropertySource} sin ese conflicto.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AssetCatalogSeedIT {

  private static final Set<String> LEGACY_ASSET_TYPES = Set.of("BONOS", "INDICE");

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  // CatalogWarmUpRunner (ApplicationRunner) llama a redisService.isCatalogLoaded() al arrancar
  // el contexto sin capturar errores de conexion; sin un Redis real el arranque fallaria.
  @Container @ServiceConnection
  static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

  @Autowired private AssetCatalogRepository catalogRepository;

  @DynamicPropertySource
  static void enableFlywayForSeedVerification(DynamicPropertyRegistry registry) {
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @Test
  void seedMigrationPopulatesAssetCatalogTable() {
    assertThat(catalogRepository.count()).isGreaterThan(0);
  }

  @Test
  void seedIncludesExpectedSymbolsAcrossAssetTypes() {
    AssetCatalogEntity aapl = catalogRepository.findById("AAPL").orElseThrow();
    assertThat(aapl.getAssetType()).isEqualTo("STOCK");
    assertThat(aapl.getCurrency()).isEqualTo("USD");

    AssetCatalogEntity voo = catalogRepository.findById("VOO").orElseThrow();
    assertThat(voo.getAssetType()).isEqualTo("ETF");

    AssetCatalogEntity cetes91 = catalogRepository.findById("CETES91").orElseThrow();
    assertThat(cetes91.getAssetType()).isEqualTo("GOVERNMENT_BOND");
    assertThat(cetes91.getCurrency()).isEqualTo("MXN");
    assertThat(cetes91.getExchange()).isEqualTo("cetesdirecto");

    AssetCatalogEntity btc = catalogRepository.findById("BTC").orElseThrow();
    assertThat(btc.getAssetType()).isEqualTo("CRYPTO");

    AssetCatalogEntity tlt = catalogRepository.findById("TLT").orElseThrow();
    assertThat(tlt.getAssetType()).isEqualTo("GOVERNMENT_BOND");
    assertThat(tlt.getCurrency()).isEqualTo("USD");
  }

  @Test
  void seedMarksAtLeastFivePopularStocksAndFiveEtfs() {
    List<AssetCatalogEntity> stocks = catalogRepository.findByAssetType("STOCK");
    List<AssetCatalogEntity> etfs = catalogRepository.findByAssetType("ETF");

    long popularStocks = stocks.stream().filter(AssetCatalogEntity::isPopular).count();
    long popularEtfs = etfs.stream().filter(AssetCatalogEntity::isPopular).count();

    assertThat(popularStocks).isGreaterThanOrEqualTo(5);
    assertThat(popularEtfs).isGreaterThanOrEqualTo(5);
  }

  @Test
  void seedDoesNotContainLegacyAssetTypeValues() {
    long legacyCount =
        catalogRepository.findAll().stream()
            .map(AssetCatalogEntity::getAssetType)
            .filter(LEGACY_ASSET_TYPES::contains)
            .count();

    assertThat(legacyCount).isZero();
  }
}
