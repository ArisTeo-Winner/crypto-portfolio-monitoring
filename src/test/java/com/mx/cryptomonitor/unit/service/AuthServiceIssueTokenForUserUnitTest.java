package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.application.service.AuditLogService;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.application.service.AuthenticationService;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.domain.model.Session;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.port.TokenIssuerPort;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AuthServiceIssueTokenForUserUnitTest {

  @Mock UserRepository userRepository;
  @Mock RefreshTokenStoreService refreshTokenStoreService;
  @Mock SessionRepository sessionRepository;
  @Mock AuthenticationService authenticationService;
  @Mock JwtUserDetailsService userDetailsService;
  @Mock TokenIssuerPort tokenIssuerPort;
  @Mock AuditLogService auditLogService;
  @Mock HttpServletRequest request;

  @InjectMocks AuthService authService;

  @Test
  void shouldPersistRefreshTokenAndSessionAndReturnJwt() {
    // Arrange
    User u = User.builder().id(UUID.randomUUID()).email("alan@example.com").build();

    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getHeader("User-Agent")).thenReturn("JUnit");

    // Los save(...) devuelven la misma entidad (y a la Session le aseguramos ID)
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(refreshTokenStoreService.store(anyString(), any(), any(), any(), any(), any()))
        .thenReturn(
            new RefreshTokenStoreService.StoredRefreshToken(
                UUID.randomUUID(), u.getId(), UUID.randomUUID(), false));
    when(sessionRepository.save(any(Session.class)))
        .thenAnswer(
            inv -> {
              Session s = inv.getArgument(0);
              if (s.getSessionId() == null)
                s.setSessionId(UUID.randomUUID()); // <— asegúrate de NO pasar null a JWT
              return s;
            });

    when(tokenIssuerPort.generateRefreshToken("alan@example.com")).thenReturn("ref-123");
    when(tokenIssuerPort.getRefreshExpiration()).thenReturn(604800L);

    // 🔧 Stub robusto:
    //  - usa nullable(UUID.class) para aceptar null si tu flujo aún no tenía ID
    //  - imprime los args para confirmar qué llega realmente
    when(tokenIssuerPort.generateAccessToken(anyString(), nullable(UUID.class)))
        .thenAnswer(
            inv -> {
              String emailArg = inv.getArgument(0, String.class);
              UUID sessArg = inv.getArgument(1, UUID.class);
              System.out.println(
                  "[TEST] generateAccessToken called with email="
                      + emailArg
                      + ", sessionId="
                      + sessArg);
              return "acc-456";
            });

    // Act
    AuthResult res = authService.issueTokensForUser(u, request);

    // Assert
    assertThat(res.accessToken()).isEqualTo("acc-456");
    assertThat(res.rawRefreshToken()).isEqualTo("ref-123");

    verify(refreshTokenStoreService)
        .store(eq("ref-123"), eq(u.getId()), any(), any(), eq("127.0.0.1"), eq("JUnit"));

    verify(sessionRepository).save(any(Session.class));
    verify(userRepository).save(any(User.class));

    // Verificación explícita de la llamada al util (si falla, no se invocó)
    verify(tokenIssuerPort, atLeastOnce()).generateAccessToken(anyString(), nullable(UUID.class));

    verifyNoMoreInteractions(
        tokenIssuerPort, refreshTokenStoreService, sessionRepository, userRepository);
  }

  // Minimal JwtTokenUtil stub interface for compile in tests; adjust if your
  // project defines it elsewhere
  /**
   * interface JwtTokenUtil { String generateRefreshToken(String email);
   *
   * <p>String generateAccessToken(String email, UUID sessionId); }
   */
}
