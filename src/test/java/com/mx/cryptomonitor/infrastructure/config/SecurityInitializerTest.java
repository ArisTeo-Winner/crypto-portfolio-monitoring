package com.mx.cryptomonitor.infrastructure.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.user.domain.model.Permission;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.PermissionRepository;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.configuration.SecurityInitializer;

@ExtendWith(MockitoExtension.class)
public class SecurityInitializerTest {

  @Mock private RoleRepository roleRepository;

  @Mock private PermissionRepository permissionRepository;

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private SecurityInitializer securityInitializer;

  @Captor private ArgumentCaptor<List<Role>> rolesCaptor;

  @Captor private ArgumentCaptor<List<Permission>> permissionsCaptor;

  @Captor private ArgumentCaptor<User> userCaptor;

  @BeforeEach
  void setUp() {
    // Usar lenient para evitar el error de UnnecessaryStubbing
    lenient()
        .when(roleRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(permissionRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void init_ShouldCreateRoles_WhenNoRolesExist() {
    // Configurar el mock para que indique que no hay roles
    when(roleRepository.count()).thenReturn(0L);

    // Ejecutar el método a probar
    securityInitializer.init();

    // Capturar y verificar los roles guardados
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<Role> savedRoles = rolesCaptor.getValue();

    // Verificar resultados
    assertNotNull(savedRoles, "Los roles guardados no deberían ser nulos");
    assertFalse(savedRoles.isEmpty(), "Debería haberse guardado al menos un rol");

    // Verificar que existan los roles específicos
    assertTrue(
        savedRoles.stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getName())),
        "Debería existir el rol ROLE_ADMIN");

    assertTrue(
        savedRoles.stream().anyMatch(role -> "ROLE_USER".equals(role.getName())),
        "Debería existir el rol ROLE_USER");
  }

  @Test
  void init_ShouldNotCreateRoles_WhenRolesExist() {
    // Configurar el mock para que indique que ya hay roles
    when(roleRepository.count()).thenReturn(3L);

    // Ejecutar el método a probar
    securityInitializer.init();

    // Verificar que no se llamó al método para guardar roles
    verify(roleRepository, never()).saveAll(any());
  }

  @Test
  void init_ShouldCreatePermissions_WhenNoPermissionsExist() {
    // Configurar el mock para que indique que no hay permisos
    when(permissionRepository.count()).thenReturn(0L);

    // Ejecutar el método a probar
    securityInitializer.init();

    // Capturar y verificar los permisos guardados
    verify(permissionRepository).saveAll(permissionsCaptor.capture());
    List<Permission> savedPermissions = permissionsCaptor.getValue();

    // Verificar resultados
    assertNotNull(savedPermissions, "Los permisos guardados no deberían ser nulos");
    assertFalse(savedPermissions.isEmpty(), "Debería haberse guardado al menos un permiso");

    // Verificar que existan permisos específicos
    assertTrue(
        savedPermissions.stream()
            .anyMatch(
                permission ->
                    permission.getName() != null && permission.getName().contains("Crear")),
        "Debería existir al menos un permiso de tipo CREATE");

    assertTrue(
        savedPermissions.stream()
            .anyMatch(
                permission -> permission.getName() != null && permission.getName().contains("Ver")),
        "Debería existir al menos un permiso de tipo READ");
  }

  @Test
  void init_ShouldCreateBootstrapAdmin_WhenEnabledAndUserDoesNotExist() {
    when(permissionRepository.count()).thenReturn(1L);
    when(roleRepository.count()).thenReturn(1L);
    Role adminRole = Role.builder().name("ROLE_ADMIN").description("Admin").build();
    when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
    when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("change-me-now")).thenReturn("encoded-password");

    ReflectionTestUtils.setField(securityInitializer, "bootstrapAdminEnabled", true);
    ReflectionTestUtils.setField(securityInitializer, "bootstrapAdminEmail", "admin@example.com");
    ReflectionTestUtils.setField(securityInitializer, "bootstrapAdminUsername", "admin");
    ReflectionTestUtils.setField(securityInitializer, "bootstrapAdminPassword", "change-me-now");

    securityInitializer.init();

    verify(userRepository).save(userCaptor.capture());
    User savedAdmin = userCaptor.getValue();
    assertEquals("admin", savedAdmin.getUsername());
    assertEquals("admin@example.com", savedAdmin.getEmail());
    assertEquals("encoded-password", savedAdmin.getPasswordHash());
    assertTrue(savedAdmin.isActive());
    assertTrue(savedAdmin.getRoles().contains(adminRole));
  }
}
