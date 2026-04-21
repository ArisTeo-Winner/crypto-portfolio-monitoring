package com.mx.cryptomonitor.unit.user.infrastructure.inbound.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.domain.exception.TooManyPasswordChangeRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.PasswordChangeRateLimiter;

class PasswordChangeRateLimiterUnitTest {

  @Test
  void validateOrThrow_enforces_5_per_10_min_and_returns_retry_after() {
    AtomicLong now = new AtomicLong(0L);
    PasswordChangeRateLimiter limiter = new PasswordChangeRateLimiter(true, 5, 600, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");

    for (int i = 0; i < 5; i++) {
      assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    }

    TooManyPasswordChangeRequestsException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> limiter.validateOrThrow(request), TooManyPasswordChangeRequestsException.class);
    assertThat(ex.getRetryAfterSeconds()).isEqualTo(600L);
  }
}
