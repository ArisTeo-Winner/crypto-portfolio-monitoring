package com.mx.cryptomonitor.domain.services;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.domain.models.RefreshToken;
import com.mx.cryptomonitor.domain.models.Session;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.SessionRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionService {


    private SessionRepository sessionRepository;

    @Transactional
    public Session createSession(User user, UUID refreshTokenId) {
    	    	    
        Session session = new Session();
        //session.setSessionId(UUID.randomUUID());
        session.setUser(user);
        session.setRefreshTokenId(refreshTokenId);
        session.setLoginTime(OffsetDateTime.now());
        session.setActive(true);
        return sessionRepository.save(session);
    }
    
    @Transactional
    public void closeSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));
        session.setLogoutTime(OffsetDateTime.now());
        session.setActive(false);
        sessionRepository.save(session);
    }
}
