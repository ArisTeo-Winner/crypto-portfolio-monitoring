package com.mx.cryptomonitor.user.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(UserService.class);

  private final UserRepository userRepository;

  private final RoleRepository roleRepository;

  private final UserMapper userMapper;

  private final PasswordEncoder passwordEncoderBean;
  private final AuditLogService auditLogService;
  private final RefreshTokenStoreService refreshTokenStoreService;
  private final SessionRepository sessionRepository;

  // Método para obtener todos los usuarios
  public List<UserResponse> getAllUsers() {

    List<User> users = userRepository.findAll();

    return users.stream().map(userMapper::toResponse).collect(Collectors.toList());
  }

  public Optional<User> findByUsername(String username) {
    return userRepository.findByUsername(username);
  }

  @Transactional
  public void deleteUserById(UUID id) {
    logger.debug("Intentando eliminar usuario con ID: {}", id);

    Optional<User> user = userRepository.findById(id);

    if (user.isEmpty()) {
      throw new IllegalArgumentException("El usuario con ID " + id + " no existe.");
    }

    sessionRepository.deleteAllByUserId(id);
    refreshTokenStoreService.revokeAllByUserId(id);
    auditLogService.detachUserFromAuditLogs(id);
    userRepository.deleteById(id);
    logger.info("User with ID {} deleted successfully", id);
  }

  @Transactional
  public Optional<UserResponse> findByEmail(String email) {
    return userRepository.findByEmailIgnoreCase(normalizeEmail(email)).map(userMapper::toResponse);
  }

  @Transactional
  public User save(User user) {
    String normalizedEmail = normalizeEmail(user.getEmail());
    user.setEmail(normalizedEmail);

    if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
      throw new IllegalArgumentException("Email already in use");
    }

    if (userRepository.findByUsername(user.getUsername()).isPresent()) {
      throw new IllegalArgumentException("Username already in use");
    }

    String encodedPassword = passwordEncoderBean.encode(user.getPasswordHash());
    user.setPasswordHash(encodedPassword);

    return userRepository.save(user);
  }

  public boolean validatePassword(String rawPassword, String encodedPassword) {
    return passwordEncoderBean.matches(rawPassword, encodedPassword);
  }

  @Transactional(readOnly = true)
  public Optional<User> findById(UUID id) {
    Optional<User> user = userRepository.findById(id);
    if (user.isPresent()) {
      logger.info("Usuario encontrado: {}", user.get());
    } else {
      logger.warn("No se encontró usuario con ID: {}", id);
    }
    return user;
  }

  @Transactional
  public User updateUser(String email, User updatedUser) {

    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("El correo electrónico no puede ser nulo o vacío.");
    }

    Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(normalizeEmail(email));

    if (optionalUser.isPresent()) {
      User user = optionalUser.get();

      user.setFirstName(
          updatedUser.getFirstName() != null ? updatedUser.getFirstName() : user.getFirstName());
      user.setLastName(
          updatedUser.getLastName() != null ? updatedUser.getLastName() : user.getLastName());
      user.setPhoneNumber(
          updatedUser.getPhoneNumber() != null
              ? updatedUser.getPhoneNumber()
              : user.getPhoneNumber());
      user.setAddress(
          updatedUser.getAddress() != null ? updatedUser.getAddress() : user.getAddress());
      user.setCity(updatedUser.getCity() != null ? updatedUser.getCity() : user.getCity());
      user.setState(updatedUser.getState() != null ? updatedUser.getState() : user.getState());
      user.setPostalCode(
          updatedUser.getPostalCode() != null ? updatedUser.getPostalCode() : user.getPostalCode());
      user.setCountry(
          updatedUser.getCountry() != null ? updatedUser.getCountry() : user.getCountry());
      user.setBio(updatedUser.getBio() != null ? updatedUser.getBio() : user.getBio());
      user.setPreferredCurrency(
          updatedUser.getPreferredCurrency() != null
              ? updatedUser.getPreferredCurrency()
              : user.getPreferredCurrency());
      user.setTimezone(
          updatedUser.getTimezone() != null ? updatedUser.getTimezone() : user.getTimezone());
      user.setUpdatedAt(LocalDateTime.now());

      return userRepository.save(user);
    } else {
      logger.error("Usuario no encontrado con el correo: " + email);
      throw new RuntimeException("Usuario no encontrado con el correo: " + email);
    }
  }

  @Transactional
  public UserResponse registerUser(UserRegistrationRequest request) {
    // Verificar si el email o username ya están en uso
    String normalizedEmail = normalizeEmail(request.email());
    boolean emailExists = userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent();
    boolean usernameExists = userRepository.findByUsername(request.username()).isPresent();
    if (emailExists || usernameExists) {
      auditLogService.log(
          AuditEventType.USER_REGISTER_CONFLICT,
          "Registration conflict detected for provided identity",
          null);
      throw new UserRegistrationConflictException("Registration conflict");
    }

    // Convertir UserRegistrationRequest a User
    User user = userMapper.toEntity(request);
    user.setEmail(normalizedEmail);

    // Encriptar la contraseña
    user.setPasswordHash(passwordEncoderBean.encode(request.password()));
    LocalDateTime now = LocalDateTime.now();
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    user.setPreferredCurrency(
        request.preferredCurrency() != null && !request.preferredCurrency().isBlank()
            ? request.preferredCurrency().toUpperCase()
            : "USD");
    user.setTimezone(
        request.timezone() != null && !request.timezone().isBlank()
            ? request.timezone()
            : "America/Mexico_City");

    // 🔥 Asegurar que los roles no sean null
    if (user.getRoles() == null) {
      user.setRoles(new ArrayList<>());
    }

    // Asignar rol predeterminado "ROLE_USER"
    Role defaultRole =
        roleRepository
            .findByName("ROLE_USER")
            .orElseThrow(() -> new RuntimeException("Rol predeterminado no encontrado"));

    user.getRoles().add(defaultRole);

    // Guardar el usuario
    User savedUser = userRepository.save(user);
    auditLogService.log(
        AuditEventType.USER_REGISTER_SUCCESS, "User registration completed", savedUser.getId());

    // Convertir User a UserResponse
    return userMapper.toResponse(savedUser);
  }

  @Transactional(readOnly = true)
  public UserResponse me(String email) {
    return findByEmail(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  @Transactional
  public UserResponse updateMe(
      String email, com.mx.cryptomonitor.user.application.dto.request.UserMeUpdateRequest request) {
    User user =
        userRepository
            .findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    user.setFirstName(request.firstName() != null ? request.firstName() : user.getFirstName());
    user.setLastName(request.lastName() != null ? request.lastName() : user.getLastName());
    user.setPhoneNumber(
        request.phoneNumber() != null ? request.phoneNumber() : user.getPhoneNumber());
    user.setAddress(request.address() != null ? request.address() : user.getAddress());
    user.setCity(request.city() != null ? request.city() : user.getCity());
    user.setState(request.state() != null ? request.state() : user.getState());
    user.setPostalCode(request.postalCode() != null ? request.postalCode() : user.getPostalCode());
    user.setCountry(request.country() != null ? request.country() : user.getCountry());
    user.setBio(request.bio() != null ? request.bio() : user.getBio());
    user.setDateOfBirth(
        request.dateOfBirth() != null ? request.dateOfBirth() : user.getDateOfBirth());
    user.setPreferredCurrency(
        request.preferredCurrency() != null && !request.preferredCurrency().isBlank()
            ? request.preferredCurrency().toUpperCase()
            : user.getPreferredCurrency());
    user.setTimezone(
        request.timezone() != null && !request.timezone().isBlank()
            ? request.timezone()
            : user.getTimezone());
    user.setUpdatedAt(LocalDateTime.now());

    return userMapper.toResponse(userRepository.save(user));
  }

  @Transactional
  public void deleteMe(String email) {
    User user =
        userRepository
            .findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    sessionRepository.deleteAllByUserId(user.getId());
    refreshTokenStoreService.revokeAllByUserId(user.getId());
    auditLogService.detachUserFromAuditLogs(user.getId());
    userRepository.deleteById(user.getId());
  }

  @Transactional
  public void requestPasswordReset(String email) {
    // Security: avoid account enumeration. No-op even if user doesn't exist.
    userRepository.findByEmailIgnoreCase(email);
  }

  @Transactional
  public void changePassword(String email, String currentPassword, String newPassword) {
    User user =
        userRepository
            .findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    String currentHash = user.getPasswordHash();
    if (currentHash == null || !passwordEncoderBean.matches(currentPassword, currentHash)) {
      throw new IllegalArgumentException("Current password is invalid");
    }
    user.setPasswordHash(passwordEncoderBean.encode(newPassword));
    user.setUpdatedAt(LocalDateTime.now());
    userRepository.save(user);
  }

  @Transactional
  public void verifyEmail(String token) {
    // Placeholder: token verification flow not yet implemented.
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Invalid verification token");
    }
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase();
  }
}
