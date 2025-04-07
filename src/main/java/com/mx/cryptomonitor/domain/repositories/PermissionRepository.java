package com.mx.cryptomonitor.domain.repositories;

import com.mx.cryptomonitor.domain.models.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);

    Optional<Permission> findByName(String name);

    List<Permission> findAllByCodeIn(List<String> codes);
}