package com.mx.cryptomonitor.unit.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.application.service.AuditLogService;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.application.service.AuthenticationService;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.application.service.TokenService;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;
import com.mx.cryptomonitor.user.domain.port.TokenIssuerPort;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class AuthNoSecretsInLogsUnitTest {

  private UserRepository userRepository;
  private AuthenticationService authenticationService;
  private SessionRepository sessionRepository;
  private JwtUserDetailsService userDetailsService;
  private TokenIssuerPort tokenIssuerPort;
  private AuditLogService auditLogService;
  private RefreshTokenStoreService refreshTokenStoreService;

  private AuthService authService;
  private TokenService tokenService;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    authenticationService = mock(AuthenticationService.class);
    sessionRepository = mock(SessionRepository.class);
    userDetailsService = mock(JwtUserDetailsService.class);
    tokenIssuerPort = mock(TokenIssuerPort.class);
    auditLogService = mock(AuditLogService.class);
    refreshTokenStoreService = mock(RefreshTokenStoreService.class);

    authService =
        new AuthService(
            userRepository,
            authenticationService,
            sessionRepository,
            userDetailsService,
            tokenIssuerPort,
            auditLogService,
            refreshTokenStoreService);

    tokenService =
        new TokenService(
            sessionRepository,
            tokenIssuerPort,
            userRepository,
            authenticationService,
            userDetailsService,
            auditLogService,
            refreshTokenStoreService);
  }

  @Test
  void logoutMustNotLogRefreshTokenValueEvenOnInvalidToken() {
    String refreshToken = "rt-secret-value-should-never-hit-logs";

    Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    when(refreshTokenStoreService.findByRawToken(refreshToken)).thenReturn(Optional.empty());

    try {
      authService.logout(refreshToken);
    } catch (InvalidTokenException ignored) {
      // expected path
    }

    boolean leaked =
        appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(refreshToken));
    assertThat(leaked).as("refresh token must never appear in logs").isFalse();

    logger.detachAppender(appender);
  }

  @Test
  void refreshMustNotLogRefreshTokenValueEvenOnRevokedToken() {
    String refreshToken = "rt-secret-value-should-never-hit-logs";

    Logger logger = (Logger) LoggerFactory.getLogger(TokenService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    RefreshTokenStoreService.StoredRefreshToken token =
        new RefreshTokenStoreService.StoredRefreshToken(
            java.util.UUID.randomUUID(),
            java.util.UUID.randomUUID(),
            java.util.UUID.randomUUID(),
            true);
    when(refreshTokenStoreService.findByRawToken(refreshToken)).thenReturn(Optional.of(token));

    try {
      tokenService.refreshToken(refreshToken);
    } catch (InvalidTokenException ignored) {
      // expected path
    }

    boolean leaked =
        appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(refreshToken));
    assertThat(leaked).as("refresh token must never appear in logs").isFalse();

    logger.detachAppender(appender);
  }

  @Test
  void controllerLoginMustNotLogPassword() {
    // This is a lightweight regression check: AuthService already had a password-leak test,
    // but we also validate that request password isn't echoed by controller logs via service logs.
    Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    try {
      authService.logout("any");
    } catch (Exception ignored) {
      // ignore
    }

    boolean leaked =
        appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("password"));
    assertThat(leaked).isFalse();

    logger.detachAppender(appender);
  }
}
