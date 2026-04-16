package com.mx.cryptomonitor.user.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.user.domain.model.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
  Optional<Role> findByName(String name);
}
