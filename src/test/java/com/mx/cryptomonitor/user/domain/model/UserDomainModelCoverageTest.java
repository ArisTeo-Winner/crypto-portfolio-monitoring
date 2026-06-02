package com.mx.cryptomonitor.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class UserDomainModelCoverageTest {

  @Test
  void authProviderEnumShouldContainExpectedValues() {
    assertThat(AuthProvider.values())
        .containsExactly(
            AuthProvider.LOCAL, AuthProvider.GOOGLE, AuthProvider.APPLE, AuthProvider.TELEGRAM);
  }

  @Test
  void userStatusEnumShouldContainExpectedValues() {
    assertThat(UserStatus.values())
        .containsExactly(UserStatus.ACTIVE, UserStatus.SUSPENDED, UserStatus.DELETED);
  }

  @Test
  void userShouldManageRolesSafely() {
    User user = new User();
    user.setRoles(null);
    Role role = createRole("ROLE_USER", "Default role");

    assertThat(user.addRole(role)).isTrue();
    assertThat(user.addRole(role)).isFalse();
    assertThat(user.getRoles()).hasSize(1);

    assertThat(user.removeRole(role)).isTrue();
    assertThat(user.removeRole(role)).isFalse();

    user.setRoles(null);
    assertThat(user.removeRole(role)).isFalse();
  }

  @Test
  void prePersistShouldInitializeUpdatedAtWhenNull() {
    User user = new User();
    user.setCreatedAt(null);
    user.setUpdatedAt(null);

    user.prePersist();

    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
  }

  @Test
  void userToStringShouldNotLeakSensitiveFields() {
    User user = createFilledUser();
    String asText = user.toString();

    assertThat(asText).doesNotContain("SecretHash!");
    assertThat(asText).doesNotContain("[1, 2, 3]");
  }

  @Test
  void userEqualsAndHashCodeShouldCoverBranches() {
    User filled = createFilledUser();
    assertEqualsHashCodeAndMutations(
        filled,
        UserDomainModelCoverageTest::copyUser,
        List.of(
            u -> u.setId(UUID.randomUUID()),
            u -> u.setUsername("other-user"),
            u -> u.setEmail("other@example.com"),
            u -> u.setPasswordHash("OtherHash"),
            u -> u.setFirstName("Other"),
            u -> u.setLastName("Person"),
            u -> u.setPhoneNumber("+529999999999"),
            u -> u.setAddress("Other street"),
            u -> u.setCity("Other city"),
            u -> u.setState("Other state"),
            u -> u.setPostalCode("11111"),
            u -> u.setCountry("US"),
            u -> u.setDateOfBirth(LocalDate.of(2000, 1, 1)),
            u -> u.setProfilePicture(new byte[] {9, 9, 9}),
            u -> u.setBio("Other bio"),
            u -> u.setActive(false),
            u -> u.setCreatedAt(LocalDateTime.of(2023, 1, 1, 1, 1)),
            u -> u.setUpdatedAt(LocalDateTime.of(2023, 1, 2, 1, 1)),
            u -> u.setLastLogin(LocalDateTime.of(2023, 1, 3, 1, 1)),
            u -> u.setRoles(new ArrayList<>(List.of(createRole("ROLE_ADMIN", "Admin role"))))));

    User allNullA = new User();
    allNullA.setRoles(null);
    allNullA.setProfilePicture(null);
    User allNullB = copyUser(allNullA);

    assertThat(allNullA).isEqualTo(allNullB);
    assertThat(allNullA.hashCode()).isEqualTo(allNullB.hashCode());
    assertThat(allNullA).isNotEqualTo(filled);
    assertThat(filled).isNotEqualTo(allNullA);
  }

  @Test
  void userAuthProviderEqualsAndHashCodeShouldCoverBranches() {
    UserAuthProvider filled = createFilledUserAuthProvider();
    assertEqualsHashCodeAndMutations(
        filled,
        UserDomainModelCoverageTest::copyUserAuthProvider,
        List.of(
            p -> p.setId(UUID.randomUUID()),
            p -> p.setUser(createMinimalUser("u3", "u3@example.com")),
            p -> p.setAuthProvider(AuthProvider.APPLE),
            p -> p.setProviderId("provider-id-2"),
            p -> p.setProviderEmail("provider2@example.com"),
            p -> p.setCreatedAt(LocalDateTime.of(2024, 2, 1, 10, 0)),
            p -> p.setLastLogin(LocalDateTime.of(2024, 2, 2, 10, 0))));

    UserAuthProvider allNullA = new UserAuthProvider();
    UserAuthProvider allNullB = copyUserAuthProvider(allNullA);
    assertThat(allNullA).isEqualTo(allNullB);
    assertThat(allNullA.hashCode()).isEqualTo(allNullB.hashCode());
    assertThat(allNullA).isNotEqualTo(filled);
    assertThat(filled).isNotEqualTo(allNullA);
  }

  @Test
  void userAuthProviderBuilderShouldCoverGeneratedBuilderMethods() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000120");
    User user = createMinimalUser("builder-user", "builder-user@example.com");
    LocalDateTime createdAt = LocalDateTime.of(2024, 3, 1, 9, 0);
    LocalDateTime lastLogin = LocalDateTime.of(2024, 3, 1, 10, 0);

    UserAuthProvider.UserAuthProviderBuilder builder =
        UserAuthProvider.builder()
            .id(id)
            .user(user)
            .authProvider(AuthProvider.APPLE)
            .providerId("apple-sub-123")
            .providerEmail("builder-user@apple.example")
            .createdAt(createdAt)
            .lastLogin(lastLogin);

    String builderText = builder.toString();
    assertThat(builderText)
        .contains("UserAuthProvider.UserAuthProviderBuilder")
        .contains("authProvider=APPLE")
        .contains("providerId=apple-sub-123")
        .contains("providerEmail=builder-user@apple.example");

    UserAuthProvider built = builder.build();
    assertThat(built.getId()).isEqualTo(id);
    assertThat(built.getUser()).isEqualTo(user);
    assertThat(built.getAuthProvider()).isEqualTo(AuthProvider.APPLE);
    assertThat(built.getProviderId()).isEqualTo("apple-sub-123");
    assertThat(built.getProviderEmail()).isEqualTo("builder-user@apple.example");
    assertThat(built.getCreatedAt()).isEqualTo(createdAt);
    assertThat(built.getLastLogin()).isEqualTo(lastLogin);
  }

  @Test
  void sessionEqualsAndHashCodeShouldCoverBranches() {
    Session filled = createFilledSession();
    assertEqualsHashCodeAndMutations(
        filled,
        UserDomainModelCoverageTest::copySession,
        List.of(
            s -> s.setSessionId(UUID.randomUUID()),
            s -> s.setUser(createMinimalUser("u4", "u4@example.com")),
            s -> s.setLoginTime(OffsetDateTime.parse("2024-02-01T10:30:00Z")),
            s -> s.setLogoutTime(OffsetDateTime.parse("2024-02-01T11:30:00Z")),
            s -> s.setActive(false)));

    Session allNullA = new Session();
    Session allNullB = copySession(allNullA);
    assertThat(allNullA).isEqualTo(allNullB);
    assertThat(allNullA.hashCode()).isEqualTo(allNullB.hashCode());
    assertThat(allNullA).isNotEqualTo(filled);
    assertThat(filled).isNotEqualTo(allNullA);
  }

  @Test
  void roleEqualsAndHashCodeShouldCoverBranches() {
    Role filled = createRole("ROLE_USER", "User role");
    filled.setId(UUID.fromString("00000000-0000-0000-0000-000000000111"));
    filled.setPermissions(new HashSet<>(Set.of(createPermission("READ", "PERM_READ", "Read"))));

    assertEqualsHashCodeAndMutations(
        filled,
        UserDomainModelCoverageTest::copyRole,
        List.of(
            r -> r.setId(UUID.randomUUID()),
            r -> r.setName("ROLE_ADMIN"),
            r -> r.setDescription("Admin role"),
            r ->
                r.setPermissions(
                    new HashSet<>(Set.of(createPermission("WRITE", "PERM_WRITE", "Write"))))));

    Role allNullA = new Role();
    allNullA.setPermissions(null);
    Role allNullB = copyRole(allNullA);
    assertThat(allNullA).isEqualTo(allNullB);
    assertThat(allNullA.hashCode()).isEqualTo(allNullB.hashCode());
    assertThat(allNullA).isNotEqualTo(filled);
    assertThat(filled).isNotEqualTo(allNullA);
  }

  @Test
  void permissionEqualsAndHashCodeShouldCoverBranches() {
    Permission filled = createPermission("READ", "PERM_READ", "Read");
    filled.setId(UUID.fromString("00000000-0000-0000-0000-000000000222"));

    assertEqualsHashCodeAndMutations(
        filled,
        UserDomainModelCoverageTest::copyPermission,
        List.of(
            p -> p.setId(UUID.randomUUID()),
            p -> p.setName("WRITE"),
            p -> p.setCode("PERM_WRITE"),
            p -> p.setDescription("Write")));

    Permission allNullA = new Permission();
    Permission allNullB = copyPermission(allNullA);
    assertThat(allNullA).isEqualTo(allNullB);
    assertThat(allNullA.hashCode()).isEqualTo(allNullB.hashCode());
    assertThat(allNullA).isNotEqualTo(filled);
    assertThat(filled).isNotEqualTo(allNullA);
  }

  private static <T> void assertEqualsHashCodeAndMutations(
      T base, Function<T, T> copier, List<Consumer<T>> mutators) {
    T copy = copier.apply(base);
    assertThat(base).isEqualTo(copy);
    assertThat(base.hashCode()).isEqualTo(copy.hashCode());
    assertThat(base).isNotEqualTo(null);
    assertThat(base).isNotEqualTo(new Object());

    for (Consumer<T> mutator : mutators) {
      T variant = copier.apply(base);
      mutator.accept(variant);
      assertThat(base).isNotEqualTo(variant);
    }
  }

  private static User createFilledUser() {
    User user = new User();
    user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    user.setUsername("test-user");
    user.setEmail("user@example.com");
    user.setPasswordHash("SecretHash!");
    user.setFirstName("Test");
    user.setLastName("User");
    user.setPhoneNumber("+5215551234567");
    user.setAddress("Main street 123");
    user.setCity("CDMX");
    user.setState("CDMX");
    user.setPostalCode("01234");
    user.setCountry("MX");
    user.setDateOfBirth(LocalDate.of(1990, 1, 15));
    user.setProfilePicture(new byte[] {1, 2, 3});
    user.setBio("Bio test");
    user.setActive(true);
    user.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
    user.setUpdatedAt(LocalDateTime.of(2024, 1, 2, 10, 0));
    user.setLastLogin(LocalDateTime.of(2024, 1, 3, 10, 0));
    user.setRoles(new ArrayList<>(List.of(createRole("ROLE_USER", "User role"))));
    return user;
  }

  private static User copyUser(User original) {
    User copy = new User();
    copy.setId(original.getId());
    copy.setUsername(original.getUsername());
    copy.setEmail(original.getEmail());
    copy.setPasswordHash(original.getPasswordHash());
    copy.setFirstName(original.getFirstName());
    copy.setLastName(original.getLastName());
    copy.setPhoneNumber(original.getPhoneNumber());
    copy.setAddress(original.getAddress());
    copy.setCity(original.getCity());
    copy.setState(original.getState());
    copy.setPostalCode(original.getPostalCode());
    copy.setCountry(original.getCountry());
    copy.setDateOfBirth(original.getDateOfBirth());
    copy.setProfilePicture(
        original.getProfilePicture() == null
            ? null
            : Arrays.copyOf(original.getProfilePicture(), original.getProfilePicture().length));
    copy.setBio(original.getBio());
    copy.setActive(original.isActive());
    copy.setCreatedAt(original.getCreatedAt());
    copy.setUpdatedAt(original.getUpdatedAt());
    copy.setLastLogin(original.getLastLogin());
    copy.setRoles(original.getRoles() == null ? null : new ArrayList<>(original.getRoles()));
    return copy;
  }

  private static UserAuthProvider createFilledUserAuthProvider() {
    UserAuthProvider provider = new UserAuthProvider();
    provider.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
    provider.setUser(createMinimalUser("oauth-user", "oauth-user@example.com"));
    provider.setAuthProvider(AuthProvider.GOOGLE);
    provider.setProviderId("provider-id-1");
    provider.setProviderEmail("provider@example.com");
    provider.setCreatedAt(LocalDateTime.of(2024, 1, 1, 8, 0));
    provider.setLastLogin(LocalDateTime.of(2024, 1, 1, 9, 0));
    return provider;
  }

  private static UserAuthProvider copyUserAuthProvider(UserAuthProvider original) {
    UserAuthProvider copy = new UserAuthProvider();
    copy.setId(original.getId());
    copy.setUser(original.getUser());
    copy.setAuthProvider(original.getAuthProvider());
    copy.setProviderId(original.getProviderId());
    copy.setProviderEmail(original.getProviderEmail());
    copy.setCreatedAt(original.getCreatedAt());
    copy.setLastLogin(original.getLastLogin());
    return copy;
  }

  private static Session createFilledSession() {
    Session session = new Session();
    session.setSessionId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
    session.setUser(createMinimalUser("session-user", "session-user@example.com"));
    session.setLoginTime(OffsetDateTime.parse("2024-01-01T10:00:00Z"));
    session.setLogoutTime(OffsetDateTime.parse("2024-01-01T11:00:00Z"));
    session.setActive(true);
    return session;
  }

  private static Session copySession(Session original) {
    Session copy = new Session();
    copy.setSessionId(original.getSessionId());
    copy.setUser(original.getUser());
    copy.setLoginTime(original.getLoginTime());
    copy.setLogoutTime(original.getLogoutTime());
    copy.setActive(original.isActive());
    return copy;
  }

  private static Role createRole(String name, String description) {
    Role role = new Role();
    role.setName(name);
    role.setDescription(description);
    role.setPermissions(new HashSet<>());
    return role;
  }

  private static Role copyRole(Role original) {
    Role copy = new Role();
    copy.setId(original.getId());
    copy.setName(original.getName());
    copy.setDescription(original.getDescription());
    copy.setPermissions(
        original.getPermissions() == null ? null : new HashSet<>(original.getPermissions()));
    return copy;
  }

  private static Permission createPermission(String name, String code, String description) {
    Permission permission = new Permission();
    permission.setName(name);
    permission.setCode(code);
    permission.setDescription(description);
    return permission;
  }

  private static Permission copyPermission(Permission original) {
    Permission copy = new Permission();
    copy.setId(original.getId());
    copy.setName(original.getName());
    copy.setCode(original.getCode());
    copy.setDescription(original.getDescription());
    return copy;
  }

  private static User createMinimalUser(String username, String email) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername(username);
    user.setEmail(email);
    return user;
  }
}
