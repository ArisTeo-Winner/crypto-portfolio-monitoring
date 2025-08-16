package com.mx.cryptomonitor.integration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

import io.lettuce.core.RedisCommandTimeoutException;

public class RedisCacheErrorHandler extends SimpleCacheErrorHandler  {
	
	  private static final Logger log = LoggerFactory.getLogger(RedisCacheErrorHandler.class);

	  @Override public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
	    if (isConnIssue(ex)) { log.warn("Redis GET error for key '{}': {} ({})", key, ex.getMessage(), ex.getClass().getSimpleName()); return; }
	    super.handleCacheGetError(ex, cache, key);
	  }

	  @Override public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
	    if (isConnIssue(ex)) { log.warn("Redis PUT error for key '{}': {} ({})", key, ex.getMessage(), ex.getClass().getSimpleName()); return; }
	    super.handleCachePutError(ex, cache, key, value);
	  }

	  @Override public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
	    if (isConnIssue(ex)) { log.warn("Redis EVICT error for key '{}': {} ({})", key, ex.getMessage(), ex.getClass().getSimpleName()); return; }
	    super.handleCacheEvictError(ex, cache, key);
	  }

	  @Override public void handleCacheClearError(RuntimeException ex, Cache cache) {
	    if (isConnIssue(ex)) { log.warn("Redis CLEAR error: {} ({})", ex.getMessage(), ex.getClass().getSimpleName()); return; }
	    super.handleCacheClearError(ex, cache);
	  }

	  private boolean isConnIssue(Throwable ex) {
	    return ex instanceof RedisConnectionFailureException
	        || ex instanceof RedisSystemException
	        || ex instanceof RedisCommandTimeoutException
	        || ex instanceof DataAccessResourceFailureException
	        || (ex.getCause() != null && isConnIssue(ex.getCause()));
	  }
}
