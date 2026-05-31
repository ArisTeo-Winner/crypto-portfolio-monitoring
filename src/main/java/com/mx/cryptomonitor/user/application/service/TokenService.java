package com.mx.cryptomonitor.user.application.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;
import com.mx.cryptomonitor.user.domain.exception.SessionNotFoundException;
import com.mx.cryptomonitor.user.domain.exception.UserNotFoundException;
import com.mx.cryptomonitor.user.domain.model.AuditEventType;
import com.mx.cryptomonitor.user.domain.model.RefreshToken;
import com.mx.cryptomonitor.user.domain.model.Session;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.port.TokenIssuerPort;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

  private final Logger logger = LoggerFactory.getLogger(TokenService.class);

  private final SessionRepository sessionRepository;
  private final TokenIssuerPort tokenIssuerPort;
  private final UserRepository userRepository;
  private final AuthenticationService authenticationService;
  private final JwtUserDetailsService userDetailsService;
  private final AuditLogService auditLogService;
  private final RefreshTokenStoreService refreshTokenStoreService;

  @Transactional
  public RefreshToken accessToken(User user, String ipAddress, String userAgent) {

    String refreshTokenValue = tokenIssuerPort.generateRefreshToken(user.getEmail());
    Session session = new Session();
    session.setUser(user);
    session.setLoginTime(OffsetDateTime.now());
    session.setActive(true);
    session.setRefreshTokenId(null);
    sessionRepository.save(session);

    LocalDateTime refreshTokenExpiry =
        LocalDateTime.now().plusSeconds(tokenIssuerPort.getRefreshExpiration() / 1000);
    RefreshTokenStoreService.StoredRefreshToken storedRefreshToken =
        refreshTokenStoreService.store(
            refreshTokenValue,
            user.getId(),
            session.getSessionId(),
            refreshTokenExpiry,
            ipAddress,
            userAgent);

    RefreshToken token = new RefreshToken();
    token.setId(storedRefreshToken.tokenId());
    token.setUser(user);
    token.setRefreshToken("REDIS_HASHED");
    token.setCreatedAt(LocalDateTime.now());
    token.setExpiresAt(refreshTokenExpiry);
    token.setIpAddress(ipAddress);
    token.setUserAgent(userAgent);
    token.setRevoked(false);
    return token;
  }

  @Transactional
  public AuthResult refreshToken(String refreshTokenValue, HttpServletRequest request) {
    RefreshTokenStoreService.StoredRefreshToken storedRefreshToken =
        refreshTokenStoreService
            .findByRawToken(refreshTokenValue)
            .orElseThrow(
                () -> {
                  auditLogService.log(
                      AuditEventType.TOKEN_REFRESH_FAILED,
                      "Refresh failed due to invalid refresh token",
                      null);
                  return new InvalidTokenException("Token de refresco invalido");
                });

    UUID userId = storedRefreshToken.userId();
    if (storedRefreshToken.revoked()) {
      auditLogService.log(
          AuditEventType.TOKEN_REFRESH_FAILED,
          "Refresh failed because token is revoked or expired",
          userId);
      throw new InvalidTokenException("Token revocado o expirado");
    }

    User user =
        userRepository
            .findById(storedRefreshToken.userId())
            .orElseThrow(
                () -> {
                  auditLogService.log(
                      AuditEventType.TOKEN_REFRESH_FAILED,
                      "Refresh failed because user was not found",
                      userId);
                  return new UserNotFoundException("Usuario no encontrado");
                });

    if (storedRefreshToken.sessionId() == null) {
      auditLogService.log(
          AuditEventType.TOKEN_REFRESH_FAILED,
          "Refresh failed because session id is missing",
          userId);
      throw new SessionNotFoundException("Sesion no encontrada");
    }

    Session session =
        sessionRepository
            .findById(storedRefreshToken.sessionId())
            .orElseThrow(
                () -> {
                  auditLogService.log(
                      AuditEventType.TOKEN_REFRESH_FAILED,
                      "Refresh failed because session was not found",
                      userId);
                  return new SessionNotFoundException("Sesion no encontrada");
                });

    closeSession(session.getSessionId());

    refreshTokenStoreService.markRevokedByRawToken(refreshTokenValue);

    String newRefreshToken = tokenIssuerPort.generateRefreshToken(user.getEmail());
    session = new Session();
    session.setUser(user);
    session.setLoginTime(OffsetDateTime.now());
    session.setActive(true);
    session.setRefreshTokenId(null);
    sessionRepository.save(session);

    // Capturar IP y UA para el nuevo token rotado — obligatorio para audit trail
    String ipAddress =
        Optional.ofNullable(request.getHeader("X-Forwarded-For"))
            .filter(s -> !s.isBlank())
            .map(xff -> xff.split(",")[0].trim())
            .orElseGet(request::getRemoteAddr);
    String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("");

    LocalDateTime refreshTokenExpiry =
        LocalDateTime.now().plusSeconds(tokenIssuerPort.getRefreshExpiration() / 1000);
    refreshTokenStoreService.store(
        newRefreshToken,
        user.getId(),
        session.getSessionId(),
        refreshTokenExpiry,
        ipAddress,
        userAgent);

    String newAccessToken =
        tokenIssuerPort.generateAccessToken(user.getEmail(), session.getSessionId());
    auditLogService.log(
        AuditEventType.TOKEN_REFRESH_SUCCESS, "Token refresh completed", user.getId());
    return new AuthResult(newAccessToken, newRefreshToken);
  }

  @Transactional
  public void revokeRefreshToken(String refreshTokenValue) {

    RefreshTokenStoreService.StoredRefreshToken storedToken =
        refreshTokenStoreService
            .findByRawToken(refreshTokenValue)
            .orElseThrow(
                () -> {
                  auditLogService.log(
                      AuditEventType.REFRESH_TOKEN_REVOKE_FAILED,
                      "Refresh token revoke failed because token was not found",
                      null);
                  return new SecurityException("Refresh token no encontrado");
                });

    refreshTokenStoreService.markRevokedByRawToken(refreshTokenValue);
    closeSession(storedToken.sessionId());
    auditLogService.log(
        AuditEventType.REFRESH_TOKEN_REVOKED, "Refresh token revoked", storedToken.userId());
  }

  @Transactional
  public void closeSession(UUID sessionId) {
    Session session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Sesion no encontrada"));
    session.setLogoutTime(OffsetDateTime.now());
    session.setActive(false);
    sessionRepository.save(session);
  }
}
