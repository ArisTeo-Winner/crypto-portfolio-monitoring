package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;

/**
 * ADR-0033 de extremo a extremo con el cableado real de Spring y un Redis real: con el mercado
 * cerrado se consulta al proveedor una sola vez y después se sirve la llave de cierre.
 *
 * <p>El reloj se fija en un sábado lejano (2040-01-07) para que la campana del lunes siga en el
 * futuro también para Redis, que expira con su propio reloj. Sin API key de Twelve Data decide el
 * calendario local y aplica el margen tras el cierre.
 */
@SpringBootTest
@ActiveProfiles("test")
class StockQuoteClosedMarketHoldIT extends InfraIntegrationTest {

  private static final Instant SATURDAY = Instant.parse("2040-01-07T15:00:00Z");
  private static final String MONDAY_BELL = "2040-01-09T14:30:00Z";

  @Autowired private MarketDataProvider marketDataProvider;
  @Autowired private CacheManager cacheManager;
  @Autowired private StringRedisTemplate redisTemplate;

  @MockitoBean(name = "alphaVantageStockQuoteProvider")
  private StockQuoteProvider alphaVantage;

  @MockitoBean(name = "massiveStockQuoteProvider")
  private StockQuoteProvider massive;

  @DynamicPropertySource
  static void enableQuoteRetention(DynamicPropertyRegistry registry) {
    registry.add("marketdata.quote.last-known.enabled", () -> "true");
    registry.add("marketdata.quote.closed-market-hold.enabled", () -> "true");
    registry.add("marketdata.twelvedata.api-key", () -> "");
    registry.add("eodhd.api-token", () -> "");
  }

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock saturdayClock() {
      return Clock.fixed(SATURDAY, ZoneOffset.UTC);
    }
  }

  @Test
  void closedMarketPriceIsFetchedOnceAndThenServedFromRedisUntilTheBell() {
    when(alphaVantage.providerName()).thenReturn("alphavantage");
    when(alphaVantage.getLatest("KO")).thenReturn(Optional.of(new BigDecimal("99.27")));

    assertThat(marketDataProvider.getLatest("KO")).contains(new BigDecimal("99.27"));
    expireStockPricesCache();
    assertThat(marketDataProvider.getLatest("KO")).contains(new BigDecimal("99.27"));
    expireStockPricesCache();
    assertThat(marketDataProvider.getLatest("KO")).contains(new BigDecimal("99.27"));

    verify(alphaVantage, times(1)).getLatest("KO");
    assertThat(redisTemplate.opsForHash().entries("marketdata:quote:close:KO"))
        .containsEntry("price", "99.27")
        .containsEntry("provider", "alphavantage")
        .containsEntry("source", "SETTLED_CHAIN")
        .containsEntry("opensAt", MONDAY_BELL);
    assertThat(redisTemplate.getExpire("marketdata:quote:close:KO")).isPositive();
  }

  @Test
  void whenEveryProviderFailsTheLastKnownPriceIsServed() {
    when(alphaVantage.providerName()).thenReturn("alphavantage");
    when(alphaVantage.getLatest("NVDA")).thenReturn(Optional.of(new BigDecimal("141.55")));
    assertThat(marketDataProvider.getLatest("NVDA")).contains(new BigDecimal("141.55"));

    // Sin llave de cierre (como tras la campana) y con la cadena caida.
    redisTemplate.delete("marketdata:quote:close:NVDA");
    expireStockPricesCache();
    when(alphaVantage.getLatest("NVDA"))
        .thenThrow(new ExternalProviderRateLimitException("alphavantage", "quota"));
    when(massive.providerName()).thenReturn("massive");

    assertThat(marketDataProvider.getLatest("NVDA")).contains(new BigDecimal("141.55"));
    assertThat(redisTemplate.hasKey("marketdata:quote:close:NVDA")).isFalse();
  }

  private void expireStockPricesCache() {
    Objects.requireNonNull(cacheManager.getCache("stockPrices")).clear();
  }
}
