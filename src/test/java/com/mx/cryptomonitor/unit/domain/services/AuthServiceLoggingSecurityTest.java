package com.mx.cryptomonitor.unit.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.user.application.dto.request.LoginRequest;
import com.mx.cryptomonitor.user.application.service.AuditLogService;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.application.service.AuthenticationService;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.domain.exception.AuthenticationException;
import com.mx.cryptomonitor.user.domain.port.TokenIssuerPort;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoggingSecurityTest {

  @Mock private UserRepository userRepository;
  @Mock private AuthenticationService authenticationService;
  @Mock private SessionRepository sessionRepository;
  @Mock private JwtUserDetailsService userDetailsService;
  @Mock private TokenIssuerPort tokenIssuerPort;
  @Mock private AuditLogService auditLogService;
  @Mock private RefreshTokenStoreService refreshTokenStoreService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            userRepository,
            authenticationService,
            sessionRepository,
            userDetailsService,
            tokenIssuerPort,
            auditLogService,
            refreshTokenStoreService);
  }

  @Test
  void login_must_not_log_password() {
    Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    assertThatThrownBy(
            () ->
                authService.login(new LoginRequest("user@example.com", "SuperSecret123!"), request))
        .isInstanceOf(AuthenticationException.class);

    boolean leaked =
        appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("SuperSecret123!"));
    assertThat(leaked).as("password must never appear in logs").isFalse();

    logger.detachAppender(appender);
  }
}
