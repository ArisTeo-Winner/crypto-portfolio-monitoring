package com.mx.cryptomonitor.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

class RedisCacheErrorHandlerTest {

  private final RedisCacheErrorHandler handler = new RedisCacheErrorHandler();

  @Test
  void shouldSwallowGetPutEvictAndClearErrors() {
    Cache cache = mock(Cache.class);
    when(cache.getName()).thenReturn("prices");
    RuntimeException exception = new RuntimeException("boom");

    assertThatCode(() -> handler.handleCacheGetError(exception, cache, "BTC"))
        .doesNotThrowAnyException();
    assertThatCode(() -> handler.handleCachePutError(exception, cache, "BTC", 123))
        .doesNotThrowAnyException();
    assertThatCode(() -> handler.handleCacheEvictError(exception, cache, "BTC"))
        .doesNotThrowAnyException();
    assertThatCode(() -> handler.handleCacheClearError(exception, cache))
        .doesNotThrowAnyException();
  }
}
