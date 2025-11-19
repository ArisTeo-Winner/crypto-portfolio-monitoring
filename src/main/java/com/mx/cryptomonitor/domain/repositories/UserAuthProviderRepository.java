package com.mx.cryptomonitor.domain.repositories;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.mx.cryptomonitor.domain.models.AuthProvider;
import com.mx.cryptomonitor.domain.models.UserAuthProvider;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, UUID>{
	Optional<UserAuthProvider> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);
	boolean existsByUserIdAndAuthProvider(UUID userId, AuthProvider authProvider);
}
