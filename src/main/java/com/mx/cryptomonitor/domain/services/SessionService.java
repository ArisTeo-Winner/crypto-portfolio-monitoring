package com.mx.cryptomonitor.domain.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.domain.models.Session;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.SessionRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class SessionService {


    private SessionRepository sessionRepository;

    @Transactional
    public Session createSession(User user) {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID());
        session.setUser(user);
        session.setLoginTime(LocalDateTime.now());
        session.setActive(true);
        return sessionRepository.save(session);
    }
    
    @Transactional
    public void closeSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));
        session.setLogoutTime(LocalDateTime.now());
        session.setActive(false);
        sessionRepository.save(session);
    }
}
