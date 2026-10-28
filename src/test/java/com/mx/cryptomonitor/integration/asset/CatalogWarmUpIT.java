package com.mx.cryptomonitor.integration.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;
import com.mx.cryptomonitor.asset.infrastructure.configuration.CatalogWarmUpRunner;
import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;

/**
 * Verifica que {@link CatalogWarmUpRunner} reconstruye Redis desde PostgreSQL cuando Redis esta
 * vacio, y que un fallo de FMP no impide que la app quede funcional con los datos persistidos.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogWarmUpIT extends InfraIntegrationTest {

  @Autowired private AssetCatalogRepository catalogRepository;
  @Autowired private CatalogStorePort catalogStorePort;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private CatalogWarmUpRunner catalogWarmUpRunner;

  @BeforeEach
  void resetState() {
    Set<String> keys = redisTemplate.keys("catalog:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
    catalogRepository.deleteAll();
  }

  @Test
  void warmUpRebuildsRedisFromPostgresWhenRedisIsEmpty() throws Exception {
    catalogRepository.save(seedEntity("AAPL", "Apple Inc.", "STOCK", "NASDAQGS", true));
    catalogRepository.save(seedEntity("VOO", "Vanguard S&P 500 ETF", "ETF", "NYSE", true));

    assertThat(catalogStorePort.isCatalogLoaded()).isFalse();

    catalogWarmUpRunner.run(new DefaultApplicationArguments());

    assertThat(redisTemplate.opsForHash().entries("catalog:entry:AAPL")).isNotEmpty();
    assertThat(redisTemplate.opsForZSet().score("catalog:search:stock", "AAPL")).isNotNull();
    assertThat(redisTemplate.opsForHash().entries("catalog:entry:VOO")).isNotEmpty();
    assertThat(redisTemplate.opsForZSet().score("catalog:search:etf", "VOO")).isNotNull();
    assertThat(catalogStorePort.isCatalogLoaded()).isTrue();
  }

  @Test
  void warmUpDoesNotFailWhenFmpIsUnreachableAndLeavesPostgresDataUsable() {
    catalogRepository.save(seedEntity("BTC", "Bitcoin", "CRYPTO", null, false));

    // fmp.base-url en el perfil de test no apunta a un servidor real: forceFullSync()
    // fallara silenciosamente para stock/etf, pero el warm-up no debe propagar la excepcion.
    assertThatCode(() -> catalogWarmUpRunner.run(new DefaultApplicationArguments()))
        .doesNotThrowAnyException();

    assertThat(catalogStorePort.findEntry("BTC")).isPresent();
  }

  private AssetCatalogEntity seedEntity(
      String symbol, String name, String assetType, String exchange, boolean popular) {
    return AssetCatalogEntity.builder()
        .symbol(symbol)
        .name(name)
        .assetType(assetType)
        .exchange(exchange)
        .currency("USD")
        .marketCap(1_000_000L)
        .popular(popular)
        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
