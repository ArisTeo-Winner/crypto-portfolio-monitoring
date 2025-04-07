package com.mx.cryptomonitor.domain.services;

import com.mx.cryptomonitor.domain.models.Permission;
import com.mx.cryptomonitor.domain.models.Role;
import com.mx.cryptomonitor.domain.repositories.PermissionRepository;
import com.mx.cryptomonitor.domain.repositories.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Role getRoleById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado con ID: " + id));
    }

    @Transactional
    public Role createRole(Role role) {
        return roleRepository.save(role);
    }

    @Transactional
    public Role addPermissionToRole(UUID roleId, UUID permissionId) {
        Role role = getRoleById(roleId);

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permiso no encontrado con ID: " + permissionId));

        role.getPermissions().add(permission);
        return roleRepository.save(role);
    }

    @Transactional
    public Role removePermissionFromRole(UUID roleId, UUID permissionId) {
        Role role = getRoleById(roleId);

        role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        return roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new EntityNotFoundException("Rol no encontrado con ID: " + id);
        }
        roleRepository.deleteById(id);
    }
}