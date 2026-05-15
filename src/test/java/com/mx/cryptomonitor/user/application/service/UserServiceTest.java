package com.mx.cryptomonitor.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mx.cryptomonitor.user.application.dto.request.UserMeUpdateRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.application.dto.response.UserResponse;
import com.mx.cryptomonitor.user.application.mapper.UserMapper;
import com.mx.cryptomonitor.user.domain.exception.UserRegistrationConflictException;
import com.mx.cryptomonitor.user.domain.model.AuditEventType;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoderBean;
  @Mock private AuditLogService auditLogService;
  @Mock private RefreshTokenStoreService refreshTokenStoreService;
  @Mock private SessionRepository sessionRepository;

  @InjectMocks private UserService userService;

  private User testUser;
  private UserResponse testResponse;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId(UUID.randomUUID());
    testUser.setEmail("test@example.com");
    testUser.setUsername("testuser");
    testUser.setPasswordHash("hashedpassword");
    testUser.setFirstName("Test");
    testUser.setActive(true);

    testResponse =
        new UserResponse(
            testUser.getUsername(),
            testUser.getEmail(),
            testUser.getFirstName(),
            testUser.getLastName(),
            testUser.getPhoneNumber(),
            testUser.getAddress(),
            testUser.getCity(),
            testUser.getState(),
            testUser.getPostalCode(),
            testUser.getCountry(),
            LocalDate.of(1990, 1, 1),
            true,
            LocalDateTime.of(2026, 3, 15, 6, 0));
  }

  @Test
  @DisplayName("getAllUsers: Returns list of mapped users")
  void getAllUsersSuccess() {
    when(userRepository.findAll()).thenReturn(Collections.singletonList(testUser));
    when(userMapper.toResponse(any(User.class))).thenReturn(testResponse);

    List<UserResponse> result = userService.getAllUsers();

    assertThat(result).containsExactly(testResponse);
  }

  @Test
  void findByUsernameShouldDelegateToRepository() {
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

    assertThat(userService.findByUsername("testuser")).contains(testUser);
  }

  @Test
  void deleteUserByIdShouldThrowWhenUserDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(userRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.deleteUserById(id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no existe");
  }

  @Test
  void deleteUserByIdShouldCascadeCleanupWhenUserExists() {
    UUID id = UUID.randomUUID();
    when(userRepository.findById(id)).thenReturn(Optional.of(testUser));

    userService.deleteUserById(id);

    verify(sessionRepository).deleteAllByUserId(id);
    verify(refreshTokenStoreService).revokeAllByUserId(id);
    verify(auditLogService).detachUserFromAuditLogs(id);
    verify(userRepository).deleteById(id);
  }

  @Test
  void findByEmailShouldReturnMappedResponse() {
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));
    when(userMapper.toResponse(testUser)).thenReturn(testResponse);

    assertThat(userService.findByEmail(testUser.getEmail())).contains(testResponse);
  }

  @Test
  void saveShouldRejectDuplicatedEmail() {
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));

    assertThatThrownBy(() -> userService.save(testUser))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Email already in use");
  }

  @Test
  void saveShouldRejectDuplicatedUsername() {
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail())).thenReturn(Optional.empty());
    when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));

    assertThatThrownBy(() -> userService.save(testUser))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Username already in use");
  }

  @Test
  void saveShouldEncodePasswordAndPersistUser() {
    User user = new User();
    user.setEmail("new@example.com");
    user.setUsername("newuser");
    user.setPasswordHash("plain-secret");
    when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.empty());
    when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.empty());
    when(passwordEncoderBean.encode("plain-secret")).thenReturn("encoded-secret");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User saved = userService.save(user);

    assertThat(saved.getPasswordHash()).isEqualTo("encoded-secret");
    verify(passwordEncoderBean).encode("plain-secret");
  }

  @Test
  void validatePasswordShouldUseConfiguredEncoder() {
    when(passwordEncoderBean.matches("secret", "encoded-secret")).thenReturn(true);
    when(passwordEncoderBean.matches("wrong", "encoded-secret")).thenReturn(false);

    assertThat(userService.validatePassword("secret", "encoded-secret")).isTrue();
    assertThat(userService.validatePassword("wrong", "encoded-secret")).isFalse();
    verify(passwordEncoderBean).matches("secret", "encoded-secret");
    verify(passwordEncoderBean).matches("wrong", "encoded-secret");
  }

  @Test
  void findByIdShouldReturnUserWhenPresent() {
    when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

    assertThat(userService.findById(testUser.getId())).contains(testUser);
  }

  @Test
  void findByIdShouldReturnEmptyWhenAbsent() {
    UUID id = UUID.randomUUID();
    when(userRepository.findById(id)).thenReturn(Optional.empty());

    assertThat(userService.findById(id)).isEmpty();
  }

  @Test
  void updateUserShouldRejectBlankEmail() {
    assertThatThrownBy(() -> userService.updateUser(" ", new User()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correo electr");
  }

  @Test
  void updateUserShouldThrowWhenUserDoesNotExist() {
    when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.updateUser("missing@example.com", new User()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Usuario no encontrado");
  }

  @Test
  void updateUserShouldMergeOnlyNonNullFields() {
    User updatedUser = new User();
    updatedUser.setFirstName("NewFirst");
    updatedUser.setCity("Monterrey");
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User result = userService.updateUser(testUser.getEmail(), updatedUser);

    assertThat(result.getFirstName()).isEqualTo("NewFirst");
    assertThat(result.getCity()).isEqualTo("Monterrey");
    assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
    assertThat(result.getUpdatedAt()).isNotNull();
  }

  @Test
  void registerUserShouldPersistUserAndAuditSuccess() {
    UserRegistrationRequest request = registrationRequest();
    Role role = Role.builder().name("ROLE_USER").build();
    User mappedUser = new User();
    mappedUser.setUsername(request.username());
    mappedUser.setEmail(request.email());
    when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.empty());
    when(userRepository.findByUsername(request.username())).thenReturn(Optional.empty());
    when(userMapper.toEntity(request)).thenReturn(mappedUser);
    when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });
    when(userMapper.toResponse(any(User.class))).thenReturn(testResponse);

    UserResponse response = userService.registerUser(request);

    assertThat(response).isEqualTo(testResponse);
    verify(userRepository)
        .save(
            argThat(
                savedUser ->
                    savedUser.getCreatedAt() != null
                        && savedUser.getUpdatedAt() != null
                        && savedUser.getUpdatedAt().isEqual(savedUser.getCreatedAt())));
    verify(auditLogService).log(any(AuditEventType.class), anyString(), any());
  }

  @Test
  void registerUserShouldForceUpdatedAtWhenMapperReturnsNullTimestamps() {
    UserRegistrationRequest request = registrationRequest();
    Role role = Role.builder().name("ROLE_USER").build();
    User mappedUser = new User();
    mappedUser.setUsername(request.username());
    mappedUser.setEmail(request.email());
    mappedUser.setCreatedAt(null);
    mappedUser.setUpdatedAt(null);

    when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.empty());
    when(userRepository.findByUsername(request.username())).thenReturn(Optional.empty());
    when(userMapper.toEntity(request)).thenReturn(mappedUser);
    when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
    when(passwordEncoderBean.encode(request.password())).thenReturn("encoded-password");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userMapper.toResponse(any(User.class))).thenReturn(testResponse);

    userService.registerUser(request);

    verify(userRepository)
        .save(
            argThat(
                savedUser ->
                    savedUser.getCreatedAt() != null
                        && savedUser.getUpdatedAt() != null
                        && savedUser.getUpdatedAt().isEqual(savedUser.getCreatedAt())));
  }

  @Test
  void registerUserShouldThrowConflictWhenEmailExists() {
    UserRegistrationRequest request = registrationRequest();
    when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(testUser));

    assertThatThrownBy(() -> userService.registerUser(request))
        .isInstanceOf(UserRegistrationConflictException.class);
    verify(auditLogService).log(any(AuditEventType.class), anyString(), any());
  }

  @Test
  void registerUserShouldThrowConflictWhenUsernameExists() {
    UserRegistrationRequest request = registrationRequest();
    when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.empty());
    when(userRepository.findByUsername(request.username())).thenReturn(Optional.of(testUser));

    assertThatThrownBy(() -> userService.registerUser(request))
        .isInstanceOf(UserRegistrationConflictException.class);
  }

  @Test
  void registerUserShouldThrowWhenDefaultRoleMissing() {
    UserRegistrationRequest request = registrationRequest();
    when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.empty());
    when(userRepository.findByUsername(request.username())).thenReturn(Optional.empty());
    when(userMapper.toEntity(request)).thenReturn(new User());
    when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.registerUser(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Rol predeterminado");
  }

  @Test
  void meShouldReturnMappedUser() {
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));
    when(userMapper.toResponse(testUser)).thenReturn(testResponse);

    assertThat(userService.me(testUser.getEmail())).isEqualTo(testResponse);
  }

  @Test
  void meShouldThrowWhenMissing() {
    when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.me("missing@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("User not found");
  }

  @Test
  void updateMeShouldMergeFieldsAndReturnMappedUser() {
    UserMeUpdateRequest request =
        new UserMeUpdateRequest("First", null, "555", null, "CDMX", null, null, null, "bio");
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userMapper.toResponse(any(User.class))).thenReturn(testResponse);

    UserResponse response = userService.updateMe(testUser.getEmail(), request);

    assertThat(response).isEqualTo(testResponse);
    assertThat(testUser.getFirstName()).isEqualTo("First");
    assertThat(testUser.getPhoneNumber()).isEqualTo("555");
    assertThat(testUser.getCity()).isEqualTo("CDMX");
    assertThat(testUser.getBio()).isEqualTo("bio");
  }

  @Test
  void updateMeShouldThrowWhenUserMissing() {
    when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                userService.updateMe(
                    "missing@example.com",
                    new UserMeUpdateRequest(null, null, null, null, null, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("User not found");
  }

  @Test
  void deleteMeShouldCascadeCleanup() {
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));

    userService.deleteMe(testUser.getEmail());

    verify(sessionRepository).deleteAllByUserId(testUser.getId());
    verify(refreshTokenStoreService).revokeAllByUserId(testUser.getId());
    verify(auditLogService).detachUserFromAuditLogs(testUser.getId());
    verify(userRepository).deleteById(testUser.getId());
  }

  @Test
  void deleteMeShouldThrowWhenMissing() {
    when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.deleteMe("missing@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("User not found");
  }

  @Test
  void requestPasswordResetShouldAlwaysQueryRepository() {
    userService.requestPasswordReset("test@example.com");

    verify(userRepository).findByEmailIgnoreCase("test@example.com");
  }

  @Test
  void changePasswordShouldPersistNewHashWhenCurrentPasswordMatches() {
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));
    when(passwordEncoderBean.matches("current", "hashedpassword")).thenReturn(true);
    when(passwordEncoderBean.encode("new")).thenReturn("newhashed");

    userService.changePassword(testUser.getEmail(), "current", "new");

    assertThat(testUser.getPasswordHash()).isEqualTo("newhashed");
    assertThat(testUser.getUpdatedAt()).isNotNull();
    verify(userRepository).save(testUser);
  }

  @Test
  void changePasswordShouldThrowWhenUserMissing() {
    when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.changePassword("missing@example.com", "current", "new"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("User not found");
  }

  @Test
  void changePasswordShouldThrowWhenCurrentPasswordDoesNotMatch() {
    when(userRepository.findByEmailIgnoreCase(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));
    when(passwordEncoderBean.matches("wrong", "hashedpassword")).thenReturn(false);

    assertThatThrownBy(() -> userService.changePassword(testUser.getEmail(), "wrong", "new"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Current password is invalid");
  }

  @Test
  void verifyEmailShouldRejectBlankTokenAndAllowValidToken() {
    assertThatThrownBy(() -> userService.verifyEmail(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid verification token");

    assertThatCode(() -> userService.verifyEmail("valid-token")).doesNotThrowAnyException();
  }

  private UserRegistrationRequest registrationRequest() {
    return new UserRegistrationRequest(
        "newuser",
        "new@example.com",
        "password",
        "First",
        "Last",
        "1234567890",
        "Address",
        "City",
        "State",
        "12345",
        "Country",
        LocalDate.of(1995, 5, 20));
  }
}
