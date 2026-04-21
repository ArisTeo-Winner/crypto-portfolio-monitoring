package com.mx.cryptomonitor.unit.user.infrastructure.inbound.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.domain.exception.TooManyRegistrationRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.UserRegistrationRateLimiter;

class UserRegistrationRateLimiterUnitTest {

  @Test
  void validateOrThrow_enforces_maxAttempts_per_window_and_exposes_retry_after() {
    AtomicLong now = new AtomicLong(0L);
    UserRegistrationRateLimiter limiter =
        new UserRegistrationRateLimiter(true, 5, 600, 300, now::get);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");

    for (int i = 0; i < 5; i++) {
      assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
    }

    TooManyRegistrationRequestsException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> limiter.validateOrThrow(request), TooManyRegistrationRequestsException.class);
    assertThat(ex.getRetryAfterSeconds()).isEqualTo(600L);

    // After the window passes, the oldest timestamps should be evicted and the request allowed
    // again.
    now.set(600_000L + 1L);
    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
  }
}
