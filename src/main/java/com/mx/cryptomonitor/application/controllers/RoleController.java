package com.mx.cryptomonitor.application.controllers;

import com.mx.cryptomonitor.application.dtos.RoleDTO;
import com.mx.cryptomonitor.application.mappers.RoleMapper;
import com.mx.cryptomonitor.domain.models.Role;
import com.mx.cryptomonitor.domain.services.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final RoleMapper roleMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        List<Role> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roleMapper.toDTOList(roles));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<RoleDTO> getRoleById(@PathVariable UUID id) {
        Role role = roleService.getRoleById(id);
        return ResponseEntity.ok(roleMapper.toDTO(role));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody RoleDTO roleDTO) {
        Role role = roleMapper.toEntity(roleDTO);
        Role savedRole = roleService.createRole(role);
        return new ResponseEntity<>(roleMapper.toDTO(savedRole), HttpStatus.CREATED);
    }

    @PutMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<RoleDTO> addPermissionToRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        Role updatedRole = roleService.addPermissionToRole(roleId, permissionId);
        return ResponseEntity.ok(roleMapper.toDTO(updatedRole));
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<RoleDTO> removePermissionFromRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        Role updatedRole = roleService.removePermissionFromRole(roleId, permissionId);
        return ResponseEntity.ok(roleMapper.toDTO(updatedRole));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}