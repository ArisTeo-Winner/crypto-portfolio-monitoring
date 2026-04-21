package com.mx.cryptomonitor.unit.user.infrastructure.inbound.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.domain.exception.TooManyMeDeleteRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeDeleteRateLimiter;

class MeDeleteRateLimiterUnitTest {

  @Test
  void validateOrThrow_enforces_2_per_hour_and_returns_retry_after() {
    AtomicLong now = new AtomicLong(0L);
    MeDeleteRateLimiter limiter = new MeDeleteRateLimiter(true, 2, 3600, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");

    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();

    TooManyMeDeleteRequestsException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> limiter.validateOrThrow(request), TooManyMeDeleteRequestsException.class);
    assertThat(ex.getRetryAfterSeconds()).isEqualTo(3600L);
  }
}
