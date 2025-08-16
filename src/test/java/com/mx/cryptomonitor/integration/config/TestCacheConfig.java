package com.mx.cryptomonitor.integration.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.jcache.config.JCacheConfigurerSupport;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.mx.cryptomonitor.infrastructure.cache.RedisCacheErrorHandler;

@Configuration
@EnableCaching
public class TestCacheConfig extends JCacheConfigurerSupport{

	@Override
	public CacheErrorHandler errorHandler() {
		return new RedisCacheErrorHandler();
	}
	
	public CacheManager cacheManager(RedisConnectionFactory cf) {
		
		RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
				.disableCachingNullValues()
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
				.computePrefixWith(name -> "test::" + name + "::");
		
		RedisCacheConfiguration testCache = base.entryTtl(Duration.ofSeconds(30));
		
		return RedisCacheManager.builder(cf)
				.cacheDefaults(base)
				.withCacheConfiguration("testCache", testCache)
				.build();
		
	}
}

