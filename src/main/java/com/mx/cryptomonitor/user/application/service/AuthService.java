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
import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
import com.mx.cryptomonitor.user.domain.exception.AuthenticationException;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;
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
  public JwtResponse login(LoginRequest loginRequest, HttpServletRequest request) {
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

      String ipAddress = request.getHeader("X-Forwarded-For");
      if (ipAddress == null || ipAddress.isEmpty()) {
        ipAddress = request.getRemoteAddr();
      }
      String userAgent = request.getHeader("User-Agent");

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
      return new JwtResponse(accessToken, refreshTokenValue);
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

  @Transactional
  public void logout(String refreshTokenValue) {
    logger.info("---AuthService >>> logout");

    RefreshTokenStoreService.StoredRefreshToken storedRefreshToken =
        refreshTokenStoreService
            .findByRawToken(refreshTokenValue)
            .orElseThrow(
                () -> {
                  auditLogService.log(
                      AuditEventType.AUTH_LOGOUT_FAILED,
                      "Logout failed due to invalid refresh token",
                      null);
                  return new InvalidTokenException("Token de refresco invalido");
                });

    UUID userId = storedRefreshToken.userId();
    if (storedRefreshToken.revoked()) {
      auditLogService.log(
          AuditEventType.AUTH_LOGOUT_FAILED,
          "Logout failed because token is revoked or expired",
          userId);
      throw new InvalidTokenException("Token ya revocado o expirado");
    }

    if (storedRefreshToken.sessionId() == null) {
      auditLogService.log(
          AuditEventType.AUTH_LOGOUT_FAILED, "Logout failed because session id is missing", userId);
      throw new InvalidTokenException("Sesion no encontrada");
    }

    refreshTokenStoreService.markRevokedByRawToken(refreshTokenValue);

    Session session =
        sessionRepository
            .findById(storedRefreshToken.sessionId())
            .orElseThrow(
                () -> {
                  auditLogService.log(
                      AuditEventType.AUTH_LOGOUT_FAILED,
                      "Logout failed because session was not found",
                      userId);
                  return new InvalidTokenException("Sesion no encontrada");
                });

    session.setActive(false);
    session.setLogoutTime(OffsetDateTime.now());
    sessionRepository.save(session);
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
  public JwtResponse issueTokensForUser(User user, HttpServletRequest request) {
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
    return new JwtResponse(accessToken, refreshTokenValue);
  }
}
