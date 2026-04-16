package com.mx.cryptomonitor.user.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.user.domain.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Optional<User> findByUsername(String username);

  Optional<User> findByEmailIgnoreCase(String email);

  // Optional<User> findByAuthProviderAndProviderId(String authProvider, String providerId);
}
