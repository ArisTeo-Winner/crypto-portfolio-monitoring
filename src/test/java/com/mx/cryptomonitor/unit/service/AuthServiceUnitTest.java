package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
import com.mx.cryptomonitor.user.application.service.AuditLogService;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.application.service.AuthenticationService;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.port.TokenIssuerPort;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

  @Test
  void issueTokensForUser_ShouldPersistSessionAndReturnTokens() {
    UserRepository userRepository = mock(UserRepository.class);
    AuthenticationService authenticationService = mock(AuthenticationService.class);

    SessionRepository sessionRepository = mock(SessionRepository.class);
    JwtUserDetailsService jwtUserDetailsService = mock(JwtUserDetailsService.class);
    AuditLogService auditLogService = mock(AuditLogService.class);
    RefreshTokenStoreService refreshTokenStoreService = mock(RefreshTokenStoreService.class);

    TokenIssuerPort tokenIssuerPort = mock(TokenIssuerPort.class);

    when(tokenIssuerPort.generateRefreshToken(anyString())).thenReturn("REFRESH_X");
    when(tokenIssuerPort.generateAccessToken(anyString(), any())).thenReturn("ACCESS_Y");
    when(tokenIssuerPort.getRefreshExpiration()).thenReturn(604800L);
    when(refreshTokenStoreService.store(anyString(), any(), any(), any(), any(), any()))
        .thenReturn(
            new RefreshTokenStoreService.StoredRefreshToken(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                false));

    AuthService svc =
        new AuthService(
            userRepository,
            authenticationService,
            sessionRepository,
            jwtUserDetailsService,
            tokenIssuerPort,
            auditLogService,
            refreshTokenStoreService);

    User u = User.builder().email("unit@example.com").username("unit").build();
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getHeader("X-Forwarded-For")).thenReturn(null);
    when(req.getRemoteAddr()).thenReturn("127.0.0.1");
    when(req.getHeader("User-Agent")).thenReturn("JUnit");

    JwtResponse resp = svc.issueTokensForUser(u, req);

    assertThat(resp.accessToken()).isEqualTo("ACCESS_Y");
    assertThat(resp.refreshToken()).isEqualTo("REFRESH_X");
    verify(refreshTokenStoreService)
        .store(eq("REFRESH_X"), any(), any(), any(), eq("127.0.0.1"), eq("JUnit"));
    verify(sessionRepository).save(any());
    verify(userRepository).save(any());
  }
}
