package com.mx.cryptomonitor.infrastructure.configuration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.jcache.config.JCacheConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.mx.cryptomonitor.infrastructure.cache.RedisCacheErrorHandler;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig extends CachingConfigurerSupport{
	
	/**/
	@Bean
	public CacheErrorHandler errorHandler() {
		return new RedisCacheErrorHandler();
	}	

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // Configuración general del caché
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)) // TTL por defecto de 5 minutos
                .disableCachingNullValues()//No cachear nulls (evitas guardar NullValue cuando no hay datos)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // Configuraciones específicas para diferentes cachés
        RedisCacheConfiguration cryptoPriceConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(1)) // Los precios de crypto se actualizan cada 30 segundos
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        RedisCacheConfiguration stockPriceConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // Los precios de acciones se actualizan cada minuto
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        RedisCacheConfiguration historicalConfig = RedisCacheConfiguration.defaultCacheConfig()
        		.entryTtl(Duration.ofDays(365 * 10))
        		.serializeKeysWith(
        				RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
        		.serializeValuesWith(
        				RedisSerializationContext.SerializationPair
                        	   .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
		
		RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
				.disableCachingNullValues()
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
				.computePrefixWith(name -> "test::" + name + "::");
		
		RedisCacheConfiguration testCache = base.entryTtl(Duration.ofSeconds(30));
		
                        
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("historicalPrices", historicalConfig)
                .withCacheConfiguration("cryptoPrices", cryptoPriceConfig)
                .withCacheConfiguration("stockPrices", stockPriceConfig)
                .withCacheConfiguration("testCache", testCache)
                .build();
    }
}