package com.mx.cryptomonitor.integration.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePoint;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPriceSeries;
import com.mx.cryptomonitor.marketdata.domain.model.AssetType;
import com.mx.cryptomonitor.marketdata.domain.model.Money;
import com.mx.cryptomonitor.marketdata.domain.model.PriceQuote;
import com.mx.cryptomonitor.marketdata.domain.model.ProviderId;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CoinMarketCapAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.coingecko.CoinGeckoHistoricalPriceAdapter;
import com.mx.cryptomonitor.shared.infrastructure.config.CacheConfig;

@Testcontainers(disabledWithoutDocker = true)
class CryptoHistoricalPricesRedisCacheIT {

  private static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static LettuceConnectionFactory redisConnectionFactory;
  private static CacheManager cacheManager;

  @BeforeAll
  static void setUpRedisCacheManager() {
    redis.start();
    redisConnectionFactory =
        new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    redisConnectionFactory.afterPropertiesSet();
    cacheManager =
        new CacheConfig()
            .cacheManager(redisConnectionFactory, Duration.ofHours(24), Duration.ofMinutes(15));
  }

  @AfterAll
  static void tearDownRedisCacheManager() {
    if (redisConnectionFactory != null) {
      redisConnectionFactory.destroy();
    }
    redis.stop();
  }

  @Test
  void cryptoHistoricalPricesCacheStoresAndReadsSeriesWithInstantTimestamp() {
    Cache cache = cacheManager.getCache(CoinGeckoHistoricalPriceAdapter.CACHE_NAME);
    CryptoHistoricalPriceSeries series =
        new CryptoHistoricalPriceSeries(
            "ethereum",
            "usd",
            List.of(
                new CryptoHistoricalPricePoint(
                    Instant.parse("2026-04-27T04:46:16Z"), new BigDecimal("2325.7592642190843"))));

    assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    assertThat(cache).isNotNull();

    cache.put("ethereum:1777242180:1777265176", series);
    CryptoHistoricalPriceSeries restored =
        cache.get("ethereum:1777242180:1777265176", CryptoHistoricalPriceSeries.class);

    assertThat(restored).isEqualTo(series);
  }

  @Test
  void cryptoPricesCacheStoresAndReadsPriceQuoteWithInstantTimestamp() {
    Cache cache = cacheManager.getCache(CoinMarketCapAdapter.CACHE_NAME);
    PriceQuote quote =
        new PriceQuote(
            "ETH",
            AssetType.CRYPTO,
            new Money(new BigDecimal("2325.7592642190843"), "USD"),
            Instant.parse("2026-04-27T04:46:16Z"),
            ProviderId.COINMARKETCAP);

    assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    assertThat(cache).isNotNull();

    cache.put("ETH", quote);
    PriceQuote restored = cache.get("ETH", PriceQuote.class);

    assertThat(restored).isEqualTo(quote);
  }
}
