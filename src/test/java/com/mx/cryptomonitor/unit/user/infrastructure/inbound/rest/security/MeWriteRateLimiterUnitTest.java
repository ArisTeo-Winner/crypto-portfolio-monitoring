package com.mx.cryptomonitor.unit.user.infrastructure.inbound.rest.security;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.domain.exception.TooManyMeWriteRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeWriteRateLimiter;

class MeWriteRateLimiterUnitTest {

  @Test
  void validateOrThrow_enforces_20_per_1_min_soft_limit_without_retry_after() {
    AtomicLong now = new AtomicLong(0L);
    MeWriteRateLimiter limiter = new MeWriteRateLimiter(true, 20, 60, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");

    for (int i = 0; i < 20; i++) {
      assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    }

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> limiter.validateOrThrow(request))
        .isInstanceOf(TooManyMeWriteRequestsException.class);
  }
}
