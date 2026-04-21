package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.marketdata.domain.exception.TooManyCryptoPriceRequestsException;

class CryptoPriceRateLimiterTest {

  @Test
  void validateOrThrowShouldDoNothingWhenLimiterIsDisabled() {
    AtomicLong clock = new AtomicLong(1_000L);
    CryptoPriceRateLimiter limiter = new CryptoPriceRateLimiter(false, 2, 60, 60, clock::get);

    assertThatCode(() -> limiter.validateOrThrow(requestWithRemoteAddr("10.0.0.1")))
        .doesNotThrowAnyException();
    assertThatCode(() -> limiter.validateOrThrow(requestWithRemoteAddr("10.0.0.1")))
        .doesNotThrowAnyException();
    assertThatCode(() -> limiter.validateOrThrow(requestWithRemoteAddr("10.0.0.1")))
        .doesNotThrowAnyException();
  }

  @Test
  void validateOrThrowShouldRejectInvalidConfiguration() {
    AtomicLong clock = new AtomicLong(1_000L);

    assertThatThrownBy(() -> new CryptoPriceRateLimiter(true, 0, 60, 60, clock::get))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void validateOrThrowShouldRejectInvalidWindowSeconds() {
    AtomicLong clock = new AtomicLong(1_000L);

    assertThatThrownBy(() -> new CryptoPriceRateLimiter(true, 1, 0, 60, clock::get))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void validateOrThrowShouldRejectInvalidCleanupIntervalSeconds() {
    AtomicLong clock = new AtomicLong(1_000L);

    assertThatThrownBy(() -> new CryptoPriceRateLimiter(true, 1, 60, 0, clock::get))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void validateOrThrowShouldRejectNullClock() {
    assertThatThrownBy(() -> new CryptoPriceRateLimiter(true, 1, 60, 60, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("clockMillis");
  }

  @Test
  void validateOrThrowShouldBlockClientWhenLimitIsExceeded() {
    AtomicLong clock = new AtomicLong(1_000L);
    CryptoPriceRateLimiter limiter = new CryptoPriceRateLimiter(true, 2, 60, 60, clock::get);
    MockHttpServletRequest request = requestWithRemoteAddr("10.0.0.1");

    limiter.validateOrThrow(request);
    limiter.validateOrThrow(request);

    assertThatThrownBy(() -> limiter.validateOrThrow(request))
        .isInstanceOf(TooManyCryptoPriceRequestsException.class)
        .satisfies(
            throwable ->
                assertThat(((TooManyCryptoPriceRequestsException) throwable).getRetryAfterSeconds())
                    .isEqualTo(60L));
  }

  @Test
  void validateOrThrowShouldUseForwardedForFirstIpAsClientKey() {
    AtomicLong clock = new AtomicLong(1_000L);
    CryptoPriceRateLimiter limiter = new CryptoPriceRateLimiter(true, 1, 60, 60, clock::get);
    MockHttpServletRequest request = requestWithRemoteAddr("10.0.0.1");
    request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

    limiter.validateOrThrow(request);

    MockHttpServletRequest secondRequest = requestWithRemoteAddr("198.51.100.99");
    secondRequest.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.99");

    assertThatThrownBy(() -> limiter.validateOrThrow(secondRequest))
        .isInstanceOf(TooManyCryptoPriceRequestsException.class);
  }

  @Test
  void validateOrThrowShouldUseUnknownClientKeyWhenRequestIsNull() {
    AtomicLong clock = new AtomicLong(1_000L);
    CryptoPriceRateLimiter limiter = new CryptoPriceRateLimiter(true, 1, 60, 60, clock::get);

    limiter.validateOrThrow(null);

    assertThatThrownBy(() -> limiter.validateOrThrow(null))
        .isInstanceOf(TooManyCryptoPriceRequestsException.class);
  }

  @Test
  void validateOrThrowShouldAllowClientAgainAfterWindowExpires() {
    AtomicLong clock = new AtomicLong(1_000L);
    CryptoPriceRateLimiter limiter = new CryptoPriceRateLimiter(true, 1, 60, 1, clock::get);
    MockHttpServletRequest request = requestWithRemoteAddr("10.0.0.1");

    limiter.validateOrThrow(request);

    clock.addAndGet(60_001L);

    assertThatCode(() -> limiter.validateOrThrow(request)).doesNotThrowAnyException();
  }

  @Test
  void validateOrThrowShouldFallbackToUnknownWhenRemoteAddrIsBlank() {
    AtomicLong clock = new AtomicLong(1_000L);
    CryptoPriceRateLimiter limiter = new CryptoPriceRateLimiter(true, 1, 60, 60, clock::get);
    MockHttpServletRequest request = requestWithRemoteAddr(" ");

    limiter.validateOrThrow(request);

    assertThatThrownBy(() -> limiter.validateOrThrow(requestWithRemoteAddr(null)))
        .isInstanceOf(TooManyCryptoPriceRequestsException.class);
  }

  @Test
  void validateOrThrowShouldFallbackToRemoteAddrWhenForwardedHeaderIsBlank() {
    AtomicLong clock = new AtomicLong(1_000L);
    CryptoPriceRateLimiter limiter = new CryptoPriceRateLimiter(true, 1, 60, 60, clock::get);
    MockHttpServletRequest request = requestWithRemoteAddr("10.0.0.1");
    request.addHeader("X-Forwarded-For", " ");

    limiter.validateOrThrow(request);

    assertThatThrownBy(() -> limiter.validateOrThrow(requestWithRemoteAddr("10.0.0.1")))
        .isInstanceOf(TooManyCryptoPriceRequestsException.class);
  }

  private MockHttpServletRequest requestWithRemoteAddr(String remoteAddr) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    return request;
  }
}
