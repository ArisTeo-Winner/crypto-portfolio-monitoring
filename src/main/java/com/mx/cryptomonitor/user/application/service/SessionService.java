package com.mx.cryptomonitor.user.application.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.user.domain.model.Session;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionService {

  private final SessionRepository sessionRepository;

  @Transactional
  public Session createSession(User user, UUID refreshTokenId) {

    Session session = new Session();
    // session.setSessionId(UUID.randomUUID());
    session.setUser(user);
    // Redis is the source of truth for refresh tokens; keep DB FK column unset.
    session.setRefreshTokenId(null);
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
}
