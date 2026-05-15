package com.mx.cryptomonitor.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableCaching
public class CacheConfig extends CachingConfigurerSupport {

  /**/
  @Bean
  public CacheErrorHandler errorHandler() {
    return new RedisCacheErrorHandler();
  }

  @Bean
  @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
  public CacheManager cacheManager(
      RedisConnectionFactory redisConnectionFactory,
      @Value("${app.cache.stock-prices-ttl:PT24H}") Duration stockPricesTtl,
      @Value("${external.providers.coingecko.cache-ttl:PT15M}") Duration coinGeckoHistoricalTtl) {
    RedisSerializer<Object> jsonSerializer =
        new GenericJackson2JsonRedisSerializer()
            .configure(objectMapper -> objectMapper.registerModule(new JavaTimeModule()));

    // ConfiguraciÃ³n general del cachÃ©
    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5)) // TTL por defecto de 5 minutos
            .disableCachingNullValues() // No cachear nulls (evitas guardar NullValue cuando no hay
            // datos)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

    // Configuraciones especÃ­ficas para diferentes cachÃ©s
    RedisCacheConfiguration cryptoPriceConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(1)) // Los precios de crypto se actualizan cada 30 segundos
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

    RedisCacheConfiguration stockPriceConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(stockPricesTtl) // TTL configurable para respetar los limites del proveedor
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

    RedisCacheConfiguration historicalConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofDays(3650L))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

    RedisCacheConfiguration coinGeckoHistoricalConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(coinGeckoHistoricalTtl)
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

    RedisCacheConfiguration base =
        RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .computePrefixWith(name -> "test::" + name + "::");

    RedisCacheConfiguration testCache = base.entryTtl(Duration.ofSeconds(30));

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(defaultConfig)
        .withCacheConfiguration("historicalPrices", historicalConfig)
        .withCacheConfiguration("cryptoHistoricalPrices", coinGeckoHistoricalConfig)
        .withCacheConfiguration("cryptoPrices", cryptoPriceConfig)
        .withCacheConfiguration("stockPrices", stockPriceConfig)
        .withCacheConfiguration("testCache", testCache)
        .build();
  }
}
