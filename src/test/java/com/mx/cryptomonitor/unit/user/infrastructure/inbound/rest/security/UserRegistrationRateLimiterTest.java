package com.mx.cryptomonitor.unit.user.infrastructure.inbound.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.domain.exception.TooManyRegistrationRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.UserRegistrationRateLimiter;

class UserRegistrationRateLimiterTest {

  @Test
  void shouldBlockWhenMaxAttemptsReachedWithinWindow() {
    UserRegistrationRateLimiter limiter = new UserRegistrationRateLimiter(true, 5, 600, 300);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.1");

    for (int i = 0; i < 5; i++) {
      limiter.validateOrThrow(request);
    }

    assertThatThrownBy(() -> limiter.validateOrThrow(request))
        .isInstanceOf(TooManyRegistrationRequestsException.class)
        .satisfies(
            ex ->
                assertThat(((TooManyRegistrationRequestsException) ex).getRetryAfterSeconds())
                    .isBetween(1L, 600L));
  }

  @Test
  void shouldAllowAgainAfterWindowExpires() {
    UserRegistrationRateLimiter limiter = new UserRegistrationRateLimiter(true, 2, 1, 300);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.20");

    limiter.validateOrThrow(request);
    limiter.validateOrThrow(request);
    try {
      Thread.sleep(1_100L);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Test thread interrupted", ex);
    }

    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
  }
}
