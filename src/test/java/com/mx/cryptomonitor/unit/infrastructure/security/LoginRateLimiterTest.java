package com.mx.cryptomonitor.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.domain.exception.TooManyLoginRequestsException;
import com.mx.cryptomonitor.user.infrastructure.security.LoginRateLimiter;

class LoginRateLimiterTest {

  @Test
  void shouldBlockIpForFiveMinutesAfterEleventhRequestWithinOneMinute() {
    AtomicLong now = new AtomicLong(0L);
    LoginRateLimiter limiter = new LoginRateLimiter(true, 10, 60, 300, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.10");

    for (int i = 0; i < 10; i++) {
      limiter.validateOrThrow(request);
    }

    assertThatThrownBy(() -> limiter.validateOrThrow(request))
        .isInstanceOf(TooManyLoginRequestsException.class)
        .satisfies(
            ex ->
                assertThat(((TooManyLoginRequestsException) ex).getRetryAfterSeconds())
                    .isEqualTo(300L));
  }

  @Test
  void shouldKeepIpBlockedUntilBlockWindowExpires() {
    AtomicLong now = new AtomicLong(0L);
    LoginRateLimiter limiter = new LoginRateLimiter(true, 10, 60, 300, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.20");

    for (int i = 0; i < 10; i++) {
      limiter.validateOrThrow(request);
    }
    assertThatThrownBy(() -> limiter.validateOrThrow(request))
        .isInstanceOf(TooManyLoginRequestsException.class);

    now.addAndGet(120_000L);

    assertThatThrownBy(() -> limiter.validateOrThrow(request))
        .isInstanceOf(TooManyLoginRequestsException.class)
        .satisfies(
            ex ->
                assertThat(((TooManyLoginRequestsException) ex).getRetryAfterSeconds())
                    .isEqualTo(180L));
  }

  @Test
  void shouldAllowRequestsAgainWhenFiveMinuteBlockHasExpired() {
    AtomicLong now = new AtomicLong(0L);
    LoginRateLimiter limiter = new LoginRateLimiter(true, 10, 60, 300, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.30");

    for (int i = 0; i < 10; i++) {
      limiter.validateOrThrow(request);
    }
    assertThatThrownBy(() -> limiter.validateOrThrow(request))
        .isInstanceOf(TooManyLoginRequestsException.class);

    now.addAndGet(300_001L);

    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
  }
}
