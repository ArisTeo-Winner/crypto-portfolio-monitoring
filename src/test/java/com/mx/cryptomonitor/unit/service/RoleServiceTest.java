package com.mx.cryptomonitor.unit.service;

import com.mx.cryptomonitor.application.dtos.RoleDTO;
import com.mx.cryptomonitor.domain.models.Permission;
import com.mx.cryptomonitor.domain.models.Role;
import com.mx.cryptomonitor.application.mappers.RoleMapper;
import com.mx.cryptomonitor.domain.repositories.PermissionRepository;
import com.mx.cryptomonitor.domain.repositories.RoleRepository;
import com.mx.cryptomonitor.domain.services.RoleService;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private RoleService roleService;

    private Role role1;
    private Role role2;
    private Permission permission;
    private UUID roleId;
    private UUID permissionId;

    @BeforeEach
    void setUp() {
        roleId = UUID.randomUUID();
        permissionId = UUID.randomUUID();

        role1 = new Role();
        role1.setId(roleId);
        role1.setName("ROLE_ADMIN");
        role1.setDescription("Administrator role");
        role1.setPermissions(new HashSet<>());

        role2 = new Role();
        role2.setId(UUID.randomUUID());
        role2.setName("ROLE_USER");
        role2.setDescription("User role");
        role2.setPermissions(new HashSet<>());

        permission = new Permission();
        permission.setId(permissionId);
        permission.setName("Create User");
        permission.setCode("USER_CREATE");
        permission.setDescription("Allows creating users");
    }

    @Test
    void getAllRoles_ShouldReturnAllRoles() {
        // Arrange
        when(roleRepository.findAll()).thenReturn(Arrays.asList(role1, role2));

        // Act
        List<Role> result = roleService.getAllRoles();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.contains(role1));
        assertTrue(result.contains(role2));
        verify(roleRepository).findAll();
    }

    @Test
    void getRoleById_WhenRoleExists_ShouldReturnRole() {
        // Arrange
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role1));

        // Act
        Role result = roleService.getRoleById(roleId);

        // Assert
        assertNotNull(result);
        assertEquals(roleId, result.getId());
        assertEquals("ROLE_ADMIN", result.getName());
        verify(roleRepository).findById(roleId);
    }

    @Test
    void getRoleById_WhenRoleDoesNotExist_ShouldThrowException() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(roleRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> roleService.getRoleById(nonExistentId));
        verify(roleRepository).findById(nonExistentId);
    }

    @Test
    void createRole_ShouldCreateAndReturnRole() {
        // Arrange
        when(roleRepository.save(any(Role.class))).thenReturn(role1);

        // Act
        Role result = roleService.createRole(role1);

        // Assert
        assertNotNull(result);
        assertEquals(roleId, result.getId());
        assertEquals("ROLE_ADMIN", result.getName());
        verify(roleRepository).save(role1);
    }

    @Test
    void addPermissionToRole_ShouldAddPermissionAndReturnUpdatedRole() {
        // Arrange
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role1));
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));
        when(roleRepository.save(any(Role.class))).thenReturn(role1);

        // Act
        Role result = roleService.addPermissionToRole(roleId, permissionId);

        // Assert
        assertNotNull(result);
        assertTrue(result.getPermissions().contains(permission));
        verify(roleRepository).findById(roleId);
        verify(permissionRepository).findById(permissionId);
        verify(roleRepository).save(role1);
    }
}