package com.mx.cryptomonitor.domain.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.domain.models.Session;

public interface SessionRepository extends JpaRepository<Session, UUID>{
	Optional<Session> findBySessionId(UUID sessionId);
    Optional<Session> findByRefreshTokenId(UUID refreshTokenId);



}
