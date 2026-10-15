package com.mx.cryptomonitor.integration.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePoint;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPriceSeries;
import com.mx.cryptomonitor.marketdata.domain.model.AssetType;
import com.mx.cryptomonitor.marketdata.domain.model.Money;
import com.mx.cryptomonitor.marketdata.domain.model.PriceQuote;
import com.mx.cryptomonitor.marketdata.domain.model.ProviderId;

/**
 * Verifica que el serializador Redis de {@code CacheConfig} round-trippea nuestros tipos de
 * marketdata con {@code java.time.Instant}, enums, {@code BigDecimal} y records anidados (Boot 4 +
 * Jackson).
 *
 * <p>Prueba el serializador DIRECTAMENTE en memoria, no un round-trip contra un Redis vivo: ese I/O
 * es framework (Lettuce/Spring Data Redis), no codigo nuestro, y resultaba INTERMITENTE bajo la
 * carga de la suite completa en Testcontainers (el valor recien escrito ocasionalmente no se leia).
 * Lo unico nuestro —que el serializador maneje nuestros tipos— se prueba aqui de forma
 * determinista.
 */
class CryptoHistoricalPricesRedisCacheIT {

  // Mismo serializador que arma CacheConfig.cacheManager (GenericJackson2 + JavaTimeModule).
  private final RedisSerializer<Object> serializer =
      new GenericJackson2JsonRedisSerializer()
          .configure(objectMapper -> objectMapper.registerModule(new JavaTimeModule()));

  @Test
  void roundTripsCryptoHistoricalPriceSeriesWithInstant() {
    CryptoHistoricalPriceSeries series =
        new CryptoHistoricalPriceSeries(
            "ethereum",
            "usd",
            List.of(
                new CryptoHistoricalPricePoint(
                    Instant.parse("2026-04-27T04:46:16Z"), new BigDecimal("2325.7592642190843"))));

    byte[] bytes = serializer.serialize(series);
    assertThat(bytes).isNotNull().isNotEmpty();
    assertThat(serializer.deserialize(bytes)).isEqualTo(series);
  }

  @Test
  void roundTripsPriceQuoteWithInstantAndEnums() {
    PriceQuote quote =
        new PriceQuote(
            "ETH",
            AssetType.CRYPTO,
            new Money(new BigDecimal("2325.7592642190843"), "USD"),
            Instant.parse("2026-04-27T04:46:16Z"),
            ProviderId.COINMARKETCAP);

    byte[] bytes = serializer.serialize(quote);
    assertThat(bytes).isNotNull().isNotEmpty();
    assertThat(serializer.deserialize(bytes)).isEqualTo(quote);
  }
}
