package com.mx.cryptomonitor.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class UserApplicationDtoCoverageTest {

  @Test
  void permissionDtoShouldCoverConstructorsAndEqualityBranches() {
    PermissionDTO base =
        new PermissionDTO(
            UUID.fromString("00000000-0000-0000-0000-000000000501"),
            "READ",
            "PERM_READ",
            "Read permission");

    PermissionDTO same =
        new PermissionDTO(base.getId(), base.getName(), base.getCode(), base.getDescription());

    assertThat(base).isEqualTo(same);
    assertThat(base.hashCode()).isEqualTo(same.hashCode());
    assertThat(base).isNotEqualTo(null);
    assertThat(base).isNotEqualTo(new Object());

    PermissionDTO mutable = new PermissionDTO();
    mutable.setId(base.getId());
    mutable.setName(base.getName());
    mutable.setCode(base.getCode());
    mutable.setDescription(base.getDescription());

    assertThat(mutable).isEqualTo(base);
    assertThat(mutable.toString()).contains("PERM_READ");

    assertMutations(
        base,
        dto -> new PermissionDTO(dto.getId(), dto.getName(), dto.getCode(), dto.getDescription()),
        Set.of(
            dto -> dto.setId(UUID.randomUUID()),
            dto -> dto.setName("WRITE"),
            dto -> dto.setCode("PERM_WRITE"),
            dto -> dto.setDescription("Write permission")));
  }

  @Test
  void roleDtoShouldCoverConstructorsAndEqualityBranches() {
    Set<PermissionDTO> permissions = new HashSet<>();
    permissions.add(
        new PermissionDTO(
            UUID.fromString("00000000-0000-0000-0000-000000000511"),
            "READ",
            "PERM_READ",
            "Read permission"));

    RoleDTO base =
        new RoleDTO(
            UUID.fromString("00000000-0000-0000-0000-000000000510"),
            "ROLE_USER",
            "Default role",
            permissions);

    RoleDTO same =
        new RoleDTO(
            base.getId(), base.getName(), base.getDescription(), new HashSet<>(permissions));
    assertThat(base).isEqualTo(same);
    assertThat(base.hashCode()).isEqualTo(same.hashCode());
    assertThat(base).isNotEqualTo(null);
    assertThat(base).isNotEqualTo(new Object());

    RoleDTO mutable = new RoleDTO();
    mutable.setId(base.getId());
    mutable.setName(base.getName());
    mutable.setDescription(base.getDescription());
    mutable.setPermissions(new HashSet<>(permissions));
    assertThat(mutable).isEqualTo(base);
    assertThat(mutable.toString()).contains("ROLE_USER");

    assertMutations(
        base,
        dto ->
            new RoleDTO(
                dto.getId(),
                dto.getName(),
                dto.getDescription(),
                clonePermissions(dto.getPermissions())),
        Set.of(
            dto -> dto.setId(UUID.randomUUID()),
            dto -> dto.setName("ROLE_ADMIN"),
            dto -> dto.setDescription("Admin role"),
            dto ->
                dto.setPermissions(
                    Set.of(
                        new PermissionDTO(
                            UUID.fromString("00000000-0000-0000-0000-000000000512"),
                            "WRITE",
                            "PERM_WRITE",
                            "Write permission")))));
  }

  @Test
  void userDtoShouldCoverSettersGettersAndEqualityBranches() {
    UserDTO base = createUserDto();
    UserDTO same = copyUserDto(base);

    assertThat(base).isEqualTo(same);
    assertThat(base.hashCode()).isEqualTo(same.hashCode());
    assertThat(base).isNotEqualTo(null);
    assertThat(base).isNotEqualTo(new Object());

    UserDTO mutable = new UserDTO();
    mutable.setId(base.getId());
    mutable.setUsername(base.getUsername());
    mutable.setEmail(base.getEmail());
    mutable.setPasswordHash(base.getPasswordHash());
    mutable.setFirstName(base.getFirstName());
    mutable.setLastName(base.getLastName());
    mutable.setPhoneNumber(base.getPhoneNumber());
    mutable.setAddress(base.getAddress());
    mutable.setCity(base.getCity());
    mutable.setState(base.getState());
    mutable.setPostalCode(base.getPostalCode());
    mutable.setCountry(base.getCountry());
    mutable.setDateOfBirth(base.getDateOfBirth());
    mutable.setProfilePicture(
        Arrays.copyOf(base.getProfilePicture(), base.getProfilePicture().length));
    mutable.setBio(base.getBio());
    mutable.setActive(base.isActive());
    mutable.setCreatedAt(base.getCreatedAt());
    mutable.setUpdatedAt(base.getUpdatedAt());
    mutable.setLastLogin(base.getLastLogin());

    assertThat(mutable).isEqualTo(base);
    assertThat(mutable.toString())
        .contains("username=test-user")
        .contains("email=user@example.com");

    assertMutations(
        base,
        UserApplicationDtoCoverageTest::copyUserDto,
        Set.of(
            dto -> dto.setId(UUID.randomUUID()),
            dto -> dto.setUsername("other-user"),
            dto -> dto.setEmail("other@example.com"),
            dto -> dto.setPasswordHash("OtherHash"),
            dto -> dto.setFirstName("Other"),
            dto -> dto.setLastName("Person"),
            dto -> dto.setPhoneNumber("+529999999999"),
            dto -> dto.setAddress("Other street"),
            dto -> dto.setCity("Other city"),
            dto -> dto.setState("Other state"),
            dto -> dto.setPostalCode("99999"),
            dto -> dto.setCountry("US"),
            dto -> dto.setDateOfBirth(LocalDate.of(2000, 1, 1)),
            dto -> dto.setProfilePicture(new byte[] {9, 9, 9}),
            dto -> dto.setBio("Other bio"),
            dto -> dto.setActive(false),
            dto -> dto.setCreatedAt(LocalDateTime.of(2024, 1, 4, 10, 0)),
            dto -> dto.setUpdatedAt(LocalDateTime.of(2024, 1, 5, 10, 0)),
            dto -> dto.setLastLogin(LocalDateTime.of(2024, 1, 6, 10, 0))));

    UserDTO nullA = new UserDTO();
    UserDTO nullB = new UserDTO();
    assertThat(nullA).isEqualTo(nullB);
    assertThat(nullA.hashCode()).isEqualTo(nullB.hashCode());
  }

  private static UserDTO createUserDto() {
    UserDTO dto = new UserDTO();
    dto.setId(UUID.fromString("00000000-0000-0000-0000-000000000520"));
    dto.setUsername("test-user");
    dto.setEmail("user@example.com");
    dto.setPasswordHash("StrongHash");
    dto.setFirstName("Test");
    dto.setLastName("User");
    dto.setPhoneNumber("+5215551234567");
    dto.setAddress("Main street 123");
    dto.setCity("CDMX");
    dto.setState("CDMX");
    dto.setPostalCode("01234");
    dto.setCountry("MX");
    dto.setDateOfBirth(LocalDate.of(1990, 1, 15));
    dto.setProfilePicture(new byte[] {1, 2, 3});
    dto.setBio("Bio test");
    dto.setActive(true);
    dto.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
    dto.setUpdatedAt(LocalDateTime.of(2024, 1, 2, 10, 0));
    dto.setLastLogin(LocalDateTime.of(2024, 1, 3, 10, 0));
    return dto;
  }

  private static UserDTO copyUserDto(UserDTO original) {
    UserDTO copy = new UserDTO();
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
    return copy;
  }

  private static Set<PermissionDTO> clonePermissions(Set<PermissionDTO> permissions) {
    Set<PermissionDTO> cloned = new HashSet<>();
    if (permissions != null) {
      for (PermissionDTO permission : permissions) {
        cloned.add(
            new PermissionDTO(
                permission.getId(),
                permission.getName(),
                permission.getCode(),
                permission.getDescription()));
      }
    }
    return cloned;
  }

  private static <T> void assertMutations(
      T base, Function<T, T> copier, Set<Consumer<T>> mutators) {
    for (Consumer<T> mutator : mutators) {
      T variant = copier.apply(base);
      mutator.accept(variant);
      assertThat(base).isNotEqualTo(variant);
    }
  }
}
