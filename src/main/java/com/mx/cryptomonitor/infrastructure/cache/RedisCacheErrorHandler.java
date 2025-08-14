package com.mx.cryptomonitor.infrastructure.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedisCacheErrorHandler implements CacheErrorHandler{

	@Override
	public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
		// TODO Auto-generated method stub
		log.warn("Redus GET error for key '{}': {} ({})", key, exception.getMessage(), exception.getClass().getSimpleName());
	}

	@Override
	public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
		// TODO Auto-generated method stub
		log.warn("Redis PUT error for key '{}': {} ({})", key, exception.getMessage(), exception.getClass().getSimpleName());				
	}

	@Override
	public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
		// TODO Auto-generated method stub
		log.warn("Redis EVICT error for key '{}': {} ({})", key, exception.getMessage(), exception.getClass().getSimpleName());
	}

	@Override
	public void handleCacheClearError(RuntimeException exception, Cache cache) {
		// TODO Auto-generated method stub
		log.warn("Redis CLEAR error for cache '{}': {} ({})", cache.getName(), exception.getMessage(), exception.getClass().getSimpleName());
	}

}
