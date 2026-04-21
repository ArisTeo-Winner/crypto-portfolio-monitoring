package com.mx.cryptomonitor.slice.jpa.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.user.domain.model.Permission;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.PermissionRepository;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

/**
 * JPA slice test (test pyramid: slice/jpa).
 *
 * <p>Validates persistence mapping of User <-> Role <-> Permission.
 *
 * <p>Note: This is not API-first; keep it as a focused persistence mapping regression test.
 */
@ActiveProfiles("test")
@DataJpaTest
class UserRoleJpaTest {

  @Autowired private PermissionRepository permissionRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void userWithRoleAndPermission_isPersistedAndLoaded() {
    Permission permission =
        permissionRepository.save(
            Permission.builder()
                // Let JPA generate the ID (setting IDs manually causes merge + stale state issues)
                .name("Create Portfolio")
                .code("PORTFOLIO_CREATE")
                .description("Crear nueva cartera")
                .build());

    Role role =
        roleRepository.save(
            Role.builder()
                .name("ROLE_TEST")
                .description("Role for testing")
                .permissions(Set.of(permission))
                .build());

    User user =
        userRepository.save(
            User.builder()
                .username("testuser")
                .email("testuser@example.com")
                .passwordHash("hashedpassword")
                .firstName("Test")
                .roles(List.of(role))
                .build());

    entityManager.flush();
    entityManager.clear();

    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getRoles()).hasSize(1);
    assertThat(reloaded.getRoles().get(0).getName()).isEqualTo("ROLE_TEST");
    assertThat(reloaded.getRoles().get(0).getPermissions())
        .extracting(Permission::getCode)
        .containsExactly("PORTFOLIO_CREATE");
  }
}
