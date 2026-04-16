package com.mx.cryptomonitor.shared.infrastructure.config;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedisCacheErrorHandler implements CacheErrorHandler {

  @Override
  public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
    log.warn(
        "Redis GET error for key '{}': {} ({})",
        key,
        exception.getMessage(),
        exception.getClass().getSimpleName());
  }

  @Override
  public void handleCachePutError(
      RuntimeException exception, Cache cache, Object key, Object value) {
    log.warn(
        "Redis PUT error for key '{}': {} ({})",
        key,
        exception.getMessage(),
        exception.getClass().getSimpleName());
  }

  @Override
  public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
    log.warn(
        "Redis EVICT error for key '{}': {} ({})",
        key,
        exception.getMessage(),
        exception.getClass().getSimpleName());
  }

  @Override
  public void handleCacheClearError(RuntimeException exception, Cache cache) {
    log.warn(
        "Redis CLEAR error for cache '{}': {} ({})",
        cache.getName(),
        exception.getMessage(),
        exception.getClass().getSimpleName());
  }
}
