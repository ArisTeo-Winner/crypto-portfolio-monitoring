package com.mx.cryptomonitor.user.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.user.domain.model.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
  Optional<Permission> findByCode(String code);

  Optional<Permission> findByName(String name);

  List<Permission> findAllByCodeIn(List<String> codes);
}
