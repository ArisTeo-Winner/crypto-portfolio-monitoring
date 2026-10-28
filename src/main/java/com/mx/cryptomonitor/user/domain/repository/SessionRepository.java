package com.mx.cryptomonitor.user.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.cryptomonitor.user.domain.model.Session;

public interface SessionRepository extends JpaRepository<Session, UUID> {
  Optional<Session> findBySessionId(UUID sessionId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from Session s where s.user.id = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);
}
