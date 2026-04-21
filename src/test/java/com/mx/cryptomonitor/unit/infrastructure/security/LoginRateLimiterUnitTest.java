package com.mx.cryptomonitor.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.domain.exception.TooManyLoginRequestsException;
import com.mx.cryptomonitor.user.infrastructure.security.LoginRateLimiter;

class LoginRateLimiterUnitTest {

  @Test
  void validateOrThrow_allows_10_per_1_min_window() {
    AtomicLong now = new AtomicLong(0L);
    LoginRateLimiter limiter = new LoginRateLimiter(true, 10, 60, 300, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");

    for (int i = 0; i < 10; i++) {
      assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    }
  }

  @Test
  void validateOrThrow_blocks_ip_for_5_min_after_10_per_1_min_exceeded() {
    AtomicLong now = new AtomicLong(0L);
    LoginRateLimiter limiter = new LoginRateLimiter(true, 10, 60, 300, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");

    for (int i = 0; i < 10; i++) {
      limiter.validateOrThrow(request);
    }

    TooManyLoginRequestsException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> limiter.validateOrThrow(request), TooManyLoginRequestsException.class);

    assertThat(ex.getRetryAfterSeconds()).isEqualTo(300L);

    // Still blocked: retry-after should decrease with time.
    now.addAndGet(1_000L);
    TooManyLoginRequestsException ex2 =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> limiter.validateOrThrow(request), TooManyLoginRequestsException.class);
    assertThat(ex2.getRetryAfterSeconds()).isEqualTo(299L);
  }

  @Test
  void validateOrThrow_uses_first_ip_from_x_forwarded_for_as_client_key() {
    AtomicLong now = new AtomicLong(0L);
    LoginRateLimiter limiter = new LoginRateLimiter(true, 1, 60, 300, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");
    request.addHeader("X-Forwarded-For", "198.51.100.7, 203.0.113.10");

    limiter.validateOrThrow(request); // consumes attempt for 198.51.100.7

    TooManyLoginRequestsException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> limiter.validateOrThrow(request), TooManyLoginRequestsException.class);
    assertThat(ex.getRetryAfterSeconds()).isEqualTo(300L);

    // Same remoteAddr but different XFF should be a different bucket, so first attempt passes.
    MockHttpServletRequest differentClient = new MockHttpServletRequest();
    differentClient.setRemoteAddr("203.0.113.10");
    differentClient.addHeader("X-Forwarded-For", "198.51.100.8, 203.0.113.10");

    assertThatCode(() -> limiter.validateOrThrow(differentClient)).doesNotThrowAnyException();
  }

  @Test
  void validateOrThrow_when_disabled_never_rate_limits() {
    AtomicLong now = new AtomicLong(0L);
    LoginRateLimiter limiter = new LoginRateLimiter(false, 1, 1, 1, 1, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");

    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
  }

  @Test
  void constructor_rejects_invalid_parameters() {
    AtomicLong now = new AtomicLong(0L);
    assertThatThrownBy(() -> new LoginRateLimiter(true, 0, 60, 300, 300, now::get))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LoginRateLimiter(true, 1, 0, 300, 300, now::get))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LoginRateLimiter(true, 1, 60, 0, 300, now::get))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LoginRateLimiter(true, 1, 60, 300, 0, now::get))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
