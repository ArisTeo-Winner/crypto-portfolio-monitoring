package com.mx.cryptomonitor.user.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.user.domain.model.AuthProvider;
import com.mx.cryptomonitor.user.domain.model.UserAuthProvider;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, UUID> {
  Optional<UserAuthProvider> findByAuthProviderAndProviderId(
      AuthProvider authProvider, String providerId);

  boolean existsByUserIdAndAuthProvider(UUID userId, AuthProvider authProvider);
}
