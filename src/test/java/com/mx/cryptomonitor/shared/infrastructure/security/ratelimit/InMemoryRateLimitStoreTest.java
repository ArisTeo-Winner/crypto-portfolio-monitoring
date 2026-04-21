package com.mx.cryptomonitor.shared.infrastructure.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreTest {

  @Test
  void shouldLimitWithinFixedWindowAndRecoverAfterWindowExpires() {
    AtomicLong clock = new AtomicLong(1_000L);
    InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock::get);

    assertThat(store.consumeFixedWindow("asset-search", "client-a", 2, 60)).isZero();
    assertThat(store.consumeFixedWindow("asset-search", "client-a", 2, 60)).isZero();
    assertThat(store.consumeFixedWindow("asset-search", "client-a", 2, 60)).isEqualTo(60L);

    clock.addAndGet(60_001L);

    assertThat(store.consumeFixedWindow("asset-search", "client-a", 2, 60)).isZero();
  }

  @Test
  void shouldBlockLoginAttemptsForConfiguredBlockWindow() {
    AtomicLong clock = new AtomicLong(1_000L);
    InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock::get);

    assertThat(store.consumeFixedWindowWithBlock("login", "client-a", 2, 60, 300)).isZero();
    assertThat(store.consumeFixedWindowWithBlock("login", "client-a", 2, 60, 300)).isZero();
    assertThat(store.consumeFixedWindowWithBlock("login", "client-a", 2, 60, 300)).isEqualTo(300L);

    clock.addAndGet(120_000L);

    assertThat(store.consumeFixedWindowWithBlock("login", "client-a", 2, 60, 300)).isEqualTo(180L);

    clock.addAndGet(181_000L);

    assertThat(store.consumeFixedWindowWithBlock("login", "client-a", 2, 60, 300)).isZero();
  }
}
