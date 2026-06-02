package com.mx.cryptomonitor.user.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.user.application.dto.response.SessionSummaryResponse;
import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService.SessionRedisEntry;
import com.mx.cryptomonitor.user.domain.exception.UserNotFoundException;
import com.mx.cryptomonitor.user.domain.model.Session;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionService {

  private final SessionRepository sessionRepository;
  private final UserRepository userRepository;
  private final RefreshTokenStoreService refreshTokenStoreService;

  public UUID resolveUserId(String email) {
    return userRepository
        .findByEmail(email)
        .map(User::getId)
        .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));
  }

  @Transactional
  public Session createSession(User user) {
    Session session = new Session();
    session.setUser(user);
    session.setLoginTime(OffsetDateTime.now());
    session.setActive(true);
    return sessionRepository.save(session);
  }

  @Transactional
  public void closeSession(UUID sessionId) {
    Session session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));
    session.setLogoutTime(OffsetDateTime.now());
    session.setActive(false);
    sessionRepository.save(session);
  }

  public List<SessionSummaryResponse> listActiveSessions(UUID userId, UUID currentSessionId) {
    List<SessionRedisEntry> redisEntries = refreshTokenStoreService.findAllActiveByUserId(userId);

    return redisEntries.stream()
        .map(
            entry -> {
              OffsetDateTime loginTime =
                  sessionRepository
                      .findById(entry.sessionId())
                      .map(Session::getLoginTime)
                      .orElse(OffsetDateTime.now());
              return new SessionSummaryResponse(
                  entry.sessionId(),
                  entry.userAgent(),
                  entry.ipAddress(),
                  loginTime,
                  loginTime,
                  entry.sessionId().equals(currentSessionId));
            })
        .toList();
  }

  @Transactional
  public void revokeSession(UUID userId, UUID sessionIdToRevoke, UUID currentSessionId) {
    if (sessionIdToRevoke.equals(currentSessionId)) {
      throw new IllegalArgumentException("No puedes revocar tu sesion actual");
    }
    refreshTokenStoreService.revokeBySessionId(userId, sessionIdToRevoke);
    sessionRepository
        .findById(sessionIdToRevoke)
        .ifPresent(
            session -> {
              session.setLogoutTime(OffsetDateTime.now());
              session.setActive(false);
              sessionRepository.save(session);
            });
  }
}
