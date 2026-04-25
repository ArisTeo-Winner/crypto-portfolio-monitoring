package com.mx.cryptomonitor.user.infrastructure.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.mx.cryptomonitor.user.domain.model.AuthProvider;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.UserAuthProviderRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private UserAuthProviderRepository userAuthProviderRepository;

  @InjectMocks private CustomOidcUserService service;

  @Test
  void loadUserFromOidcUserShouldPersistNonNullUpdatedAtForNewUser() {
    Role defaultRole = defaultRole();
    List<TimestampSnapshot> persistedSnapshots = new ArrayList<>();

    when(userAuthProviderRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "oidc-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmailIgnoreCase("oidc@example.com")).thenReturn(Optional.empty());
    when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(defaultRole));
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              persistedSnapshots.add(new TimestampSnapshot(user.getCreatedAt(), user.getUpdatedAt()));
              if (user.getId() == null) {
                user.setId(UUID.randomUUID());
              }
              return user;
            });

    OidcUser oidcUser = oidcUser();

    OidcUserWithDomain principal = service.loadUserFromOidcUser(oidcUser);

    assertThat(persistedSnapshots).isNotEmpty();
    assertThat(persistedSnapshots)
        .allSatisfy(
            snapshot -> {
              assertThat(snapshot.createdAt()).isNotNull();
              assertThat(snapshot.updatedAt()).isNotNull();
            });
    assertThat(persistedSnapshots.get(0).updatedAt()).isEqualTo(persistedSnapshots.get(0).createdAt());
    assertThat(principal.domainUser().getCreatedAt()).isNotNull();
    assertThat(principal.domainUser().getUpdatedAt()).isNotNull();

    verify(userAuthProviderRepository).save(any());
  }

  private OidcUser oidcUser() {
    Map<String, Object> claims =
        Map.of(
            "sub", "oidc-sub",
            "email", "oidc@example.com",
            "given_name", "Alicia",
            "family_name", "Rivera");

    OidcIdToken idToken =
        new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300), claims);
    OidcUserInfo userInfo = new OidcUserInfo(claims);
    return new DefaultOidcUser(new HashSet<>(), idToken, userInfo);
  }

  private Role defaultRole() {
    Role role = new Role();
    role.setName("ROLE_USER");
    role.setDescription("Default user role");
    role.setPermissions(new HashSet<>());
    return role;
  }

  private record TimestampSnapshot(LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
