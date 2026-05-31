package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mx.cryptomonitor.user.application.service.AuditLogService;
import com.mx.cryptomonitor.user.application.service.AuthenticationService;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.application.service.TokenService;
import com.mx.cryptomonitor.user.domain.model.RefreshToken;
import com.mx.cryptomonitor.user.domain.model.Session;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.port.TokenIssuerPort;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

  private final Logger logger = LoggerFactory.getLogger(TokenServiceTest.class);

  @Mock private RefreshTokenStoreService refreshTokenStoreService;

  @Mock private SessionRepository sessionRepository;

  @Mock private TokenIssuerPort tokenIssuerPort;
  @Mock private UserRepository userRepository;
  @Mock private AuthenticationService authenticationService;
  @Mock private JwtUserDetailsService userDetailsService;
  @Mock private AuditLogService auditLogService;

  @InjectMocks private TokenService tokenService;

  @Test
  void accessToken_should_persist_refresh_token_with_expected_expiration() {
    logger.info(
        "---TokenServiceTest >>> accessToken_should_persist_refresh_token_with_expected_expiration()---");

    User user = User.builder().email("token@example.com").username("token_user").build();

    when(tokenIssuerPort.generateRefreshToken("token@example.com")).thenReturn("refresh-abc");
    when(tokenIssuerPort.getRefreshExpiration()).thenReturn(120_000L); // 120s
    when(refreshTokenStoreService.store(
            eq("refresh-abc"), eq(user.getId()), any(), any(), eq("127.0.0.1"), eq("JUnit")))
        .thenReturn(
            new RefreshTokenStoreService.StoredRefreshToken(
                java.util.UUID.randomUUID(), user.getId(), java.util.UUID.randomUUID(), false));

    RefreshToken saved = tokenService.accessToken(user, "127.0.0.1", "JUnit");

    assertThat(saved.getRefreshToken()).isEqualTo("REDIS_HASHED");
    assertThat(saved.getUser()).isEqualTo(user);
    assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
    assertThat(saved.getUserAgent()).isEqualTo("JUnit");
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getExpiresAt()).isNotNull();
    assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());

    verify(tokenIssuerPort).generateRefreshToken("token@example.com");
    verify(refreshTokenStoreService)
        .store(eq("refresh-abc"), eq(user.getId()), any(), any(), eq("127.0.0.1"), eq("JUnit"));
    verify(tokenIssuerPort).getRefreshExpiration();
  }

  @Test
  void revokeRefreshToken_should_mark_token_as_revoked() {
    UUID sessionId = UUID.randomUUID();
    RefreshTokenStoreService.StoredRefreshToken existing =
        new RefreshTokenStoreService.StoredRefreshToken(
            UUID.randomUUID(), UUID.randomUUID(), sessionId, false);

    Session session = new Session();
    session.setActive(true);

    when(refreshTokenStoreService.findByRawToken("refresh-xyz")).thenReturn(Optional.of(existing));
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    tokenService.revokeRefreshToken("refresh-xyz");

    verify(refreshTokenStoreService).findByRawToken("refresh-xyz");
    verify(refreshTokenStoreService).markRevokedByRawToken("refresh-xyz");
    verify(sessionRepository).findById(sessionId);
    verify(sessionRepository).save(session);
    assertThat(session.isActive()).isFalse();
    assertThat(session.getLogoutTime()).isNotNull();
  }

  @Test
  void revokeRefreshToken_should_throw_when_token_missing() {
    when(refreshTokenStoreService.findByRawToken("missing")).thenReturn(Optional.empty());

    SecurityException ex =
        assertThrows(SecurityException.class, () -> tokenService.revokeRefreshToken("missing"));

    assertThat(ex.getMessage()).isEqualTo("Refresh token no encontrado");
  }
}
