package com.mx.cryptomonitor.unit.user.infrastructure.inbound.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.domain.exception.TooManyPasswordResetRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.PasswordResetRateLimiter;

class PasswordResetRateLimiterUnitTest {

  @Test
  void validateOrThrow_enforces_3_per_15_min_and_returns_retry_after() {
    AtomicLong now = new AtomicLong(0L);
    PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(true, 3, 900, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");

    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();

    TooManyPasswordResetRequestsException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> limiter.validateOrThrow(request), TooManyPasswordResetRequestsException.class);
    assertThat(ex.getRetryAfterSeconds()).isEqualTo(900L);

    now.addAndGet(1_000L);
    TooManyPasswordResetRequestsException ex2 =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> limiter.validateOrThrow(request), TooManyPasswordResetRequestsException.class);
    assertThat(ex2.getRetryAfterSeconds()).isEqualTo(899L);

    now.set(900_000L + 1L);
    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
  }
}
