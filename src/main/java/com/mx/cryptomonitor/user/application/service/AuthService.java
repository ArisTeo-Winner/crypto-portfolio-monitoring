package com.mx.cryptomonitor.user.application.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.user.application.dto.request.LoginRequest;
import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.domain.exception.AuthenticationException;
import com.mx.cryptomonitor.user.domain.model.AuditEventType;
import com.mx.cryptomonitor.user.domain.model.Session;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.port.TokenIssuerPort;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final Logger logger = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final AuthenticationService authenticationService;
  private final SessionRepository sessionRepository;
  private final JwtUserDetailsService userDetailsService;
  private final TokenIssuerPort tokenIssuerPort;
  private final AuditLogService auditLogService;
  private final RefreshTokenStoreService refreshTokenStoreService;

  @Transactional
  public AuthResult login(LoginRequest loginRequest, HttpServletRequest request) {
    User user = null;
    String normalizedEmail = normalizeEmail(loginRequest.email());
    try {
      user =
          userRepository
              .findByEmailIgnoreCase(normalizedEmail)
              .orElseThrow(() -> new AuthenticationException("Credenciales invalidas"));

      user = authenticationService.authenticate(normalizedEmail, loginRequest.password());
      UserDetails userDetails = userDetailsService.loadUserByUsername(normalizedEmail);
      if (userDetails == null) {
        throw new AuthenticationException("Credenciales invalidas");
      }

      // Patrón canónico: primera IP de X-Forwarded-For (cliente real, no proxies intermedios)
      String ipAddress =
          Optional.ofNullable(request.getHeader("X-Forwarded-For"))
              .filter(s -> !s.isBlank())
              .map(xff -> xff.split(",")[0].trim())
              .orElseGet(request::getRemoteAddr);
      String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("");

      String refreshTokenValue = tokenIssuerPort.generateRefreshToken(user.getEmail());
      Session session = new Session();
      session.setUser(user);
      session.setLoginTime(OffsetDateTime.now());
      session.setActive(true);
      session.setRefreshTokenId(null);
      sessionRepository.save(session);

      LocalDateTime refreshTokenExpiry =
          LocalDateTime.now().plusSeconds(tokenIssuerPort.getRefreshExpiration() / 1000);
      refreshTokenStoreService.store(
          refreshTokenValue,
          user.getId(),
          session.getSessionId(),
          refreshTokenExpiry,
          ipAddress,
          userAgent);

      user.setLastLogin(LocalDateTime.now());
      userRepository.save(user);

      String accessToken =
          tokenIssuerPort.generateAccessToken(user.getEmail(), session.getSessionId());
      auditLogService.log(
          AuditEventType.AUTH_LOGIN_SUCCESS, "Authentication succeeded", user.getId(), request);
      return new AuthResult(accessToken, refreshTokenValue);
    } catch (RuntimeException ex) {
      auditLogService.log(
          AuditEventType.AUTH_LOGIN_FAILED,
          "Authentication failed",
          user != null ? user.getId() : null,
          request);
      throw ex;
    }
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase();
  }

  /**
   * Revoca la sesión asociada al refresh token.
   *
   * <p>El logout es <b>idempotente</b>: si el token ya no existe, está revocado o su sesión no
   * existe, se responde con éxito silencioso. Esto sigue la recomendación OWASP de no revelar al
   * cliente si el token era válido o no (evita oracle de tokens).
   */
  @Transactional
  public void logout(String refreshTokenValue) {

    Optional<RefreshTokenStoreService.StoredRefreshToken> maybeToken =
        refreshTokenStoreService.findByRawToken(refreshTokenValue);

    // Token inexistente o ya revocado → logout silencioso (no revela validez del token)
    if (maybeToken.isEmpty() || maybeToken.get().revoked()) {
      auditLogService.log(
          AuditEventType.AUTH_LOGOUT_SUCCESS, "Logout (token already invalid or absent)", null);
      return;
    }

    RefreshTokenStoreService.StoredRefreshToken storedRefreshToken = maybeToken.get();
    UUID userId = storedRefreshToken.userId();

    refreshTokenStoreService.markRevokedByRawToken(refreshTokenValue);

    if (storedRefreshToken.sessionId() != null) {
      sessionRepository
          .findById(storedRefreshToken.sessionId())
          .ifPresent(
              session -> {
                session.setActive(false);
                session.setLogoutTime(OffsetDateTime.now());
                sessionRepository.save(session);
              });
    }

    auditLogService.log(AuditEventType.AUTH_LOGOUT_SUCCESS, "Logout completed", userId);
  }

  public Session createSession(User user) {
    Session session = new Session();
    session.setUser(user);
    session.setLoginTime(OffsetDateTime.now());
    session.setActive(true);
    session.setRefreshTokenId(null);
    return sessionRepository.save(session);
  }

  @Transactional
  public AuthResult issueTokensForUser(User user, HttpServletRequest request) {
    String ipAddress =
        Optional.ofNullable(request.getHeader("X-Forwarded-For"))
            .filter(s -> !s.isBlank())
            .orElseGet(request::getRemoteAddr);
    String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");

    String refreshTokenValue = tokenIssuerPort.generateRefreshToken(user.getEmail());
    Session session = new Session();
    session.setUser(user);
    session.setLoginTime(OffsetDateTime.now());
    session.setActive(true);
    session.setRefreshTokenId(null);
    sessionRepository.save(session);

    LocalDateTime refreshTokenExpiry =
        LocalDateTime.now().plusSeconds(tokenIssuerPort.getRefreshExpiration() / 1000);
    refreshTokenStoreService.store(
        refreshTokenValue,
        user.getId(),
        session.getSessionId(),
        refreshTokenExpiry,
        ipAddress,
        userAgent);

    user.setLastLogin(LocalDateTime.now());
    userRepository.save(user);

    String accessToken =
        tokenIssuerPort.generateAccessToken(user.getEmail(), session.getSessionId());
    auditLogService.log(
        AuditEventType.AUTH_LOGIN_SUCCESS, "Token issue completed", user.getId(), request);
    return new AuthResult(accessToken, refreshTokenValue);
  }
}
