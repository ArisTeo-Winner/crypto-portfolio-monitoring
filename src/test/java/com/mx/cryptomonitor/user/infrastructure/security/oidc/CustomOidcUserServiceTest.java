package com.mx.cryptomonitor.user.infrastructure.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
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
import com.mx.cryptomonitor.user.domain.model.UserAuthProvider;
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
    ArgumentCaptor<UserAuthProvider> linkCaptor = ArgumentCaptor.forClass(UserAuthProvider.class);

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
    assertThat(principal.getAuthorities()).extracting("authority").contains("ROLE_USER");

    verify(userAuthProviderRepository).save(linkCaptor.capture());
    assertThat(linkCaptor.getValue().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(linkCaptor.getValue().getProviderId()).isEqualTo("oidc-sub");
    assertThat(linkCaptor.getValue().getProviderEmail()).isEqualTo("oidc@example.com");
    assertThat(principal.domainUser().getRoles()).extracting(Role::getName).contains("ROLE_USER");
  }

  @Test
  void loadUserFromOidcUserShouldLinkExistingLocalUserByEmailWithoutCreatingDuplicateUser() {
    Role existingRole = defaultRole();
    User existingUser =
        User.builder()
            .id(UUID.randomUUID())
            .email("oidc@example.com")
            .username("existing-user")
            .createdAt(LocalDateTime.now().minusDays(3))
            .updatedAt(LocalDateTime.now().minusDays(1))
            .roles(new ArrayList<>(List.of(existingRole)))
            .build();
    ArgumentCaptor<UserAuthProvider> linkCaptor = ArgumentCaptor.forClass(UserAuthProvider.class);

    when(userAuthProviderRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "oidc-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmailIgnoreCase("oidc@example.com")).thenReturn(Optional.of(existingUser));

    OidcUserWithDomain principal = service.loadUserFromOidcUser(oidcUser());

    assertThat(principal.domainUser()).isSameAs(existingUser);
    assertThat(principal.getAuthorities()).extracting("authority").contains("ROLE_USER");

    verify(userRepository, never()).save(any(User.class));
    verify(userAuthProviderRepository).save(linkCaptor.capture());
    assertThat(linkCaptor.getValue().getUser()).isSameAs(existingUser);
    assertThat(linkCaptor.getValue().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(linkCaptor.getValue().getProviderId()).isEqualTo("oidc-sub");
  }

  @Test
  void loadUserFromOidcUserShouldReuseExistingGoogleLink() {
    User existingUser =
        User.builder()
            .id(UUID.randomUUID())
            .email("oidc@example.com")
            .username("existing-user")
            .createdAt(LocalDateTime.now().minusDays(3))
            .updatedAt(LocalDateTime.now().minusDays(1))
            .roles(new ArrayList<>(List.of(defaultRole())))
            .build();
    UserAuthProvider existingLink =
        UserAuthProvider.builder()
            .user(existingUser)
            .authProvider(AuthProvider.GOOGLE)
            .providerId("oidc-sub")
            .providerEmail("oidc@example.com")
            .createdAt(LocalDateTime.now().minusDays(2))
            .build();

    when(userAuthProviderRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "oidc-sub"))
        .thenReturn(Optional.of(existingLink));

    OidcUserWithDomain principal = service.loadUserFromOidcUser(oidcUser());

    assertThat(principal.domainUser()).isSameAs(existingUser);
    assertThat(existingLink.getLastLogin()).isNotNull();
    assertThat(principal.getAuthorities()).extracting("authority").contains("ROLE_USER");

    verify(userRepository, never()).save(any(User.class));
    verify(userAuthProviderRepository, never()).save(any(UserAuthProvider.class));
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
