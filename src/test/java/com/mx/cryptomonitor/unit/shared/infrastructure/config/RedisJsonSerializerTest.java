package com.mx.cryptomonitor.unit.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePoint;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPriceSeries;

class RedisJsonSerializerTest {

  @Test
  void serializesCryptoHistoricalPriceSeriesWithInstantTimestamp() {
    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer()
            .configure(objectMapper -> objectMapper.registerModule(new JavaTimeModule()));
    CryptoHistoricalPriceSeries series =
        new CryptoHistoricalPriceSeries(
            "ethereum",
            "usd",
            List.of(
                new CryptoHistoricalPricePoint(
                    Instant.parse("2026-04-27T04:46:16Z"), new BigDecimal("2325.7592642190843"))));

    byte[] payload = serializer.serialize(series);
    CryptoHistoricalPriceSeries restored =
        serializer.deserialize(payload, CryptoHistoricalPriceSeries.class);

    assertThat(payload).isNotEmpty();
    assertThat(restored).isEqualTo(series);
  }
}
