package com.mx.cryptomonitor.user.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.user.domain.model.RefreshToken;
import com.mx.cryptomonitor.user.domain.model.User;

/**
 * Legacy SQL repository.
 *
 * <p>The active login/refresh/logout flow uses Redis via {@code RefreshTokenStoreService} as the
 * operational source of truth for refresh tokens. This repository is retained only for backward
 * compatibility and should not be treated as the primary runtime store.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  Optional<RefreshToken> findByRefreshToken(String refreshToken);

  List<RefreshToken> findByUser(User user);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from RefreshToken rt where rt.user.id = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);
}
