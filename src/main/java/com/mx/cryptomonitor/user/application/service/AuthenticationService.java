package com.mx.cryptomonitor.user.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@Service
public class AuthenticationService {

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordValidationService passwordValidationService;

  /**
   * Autentica al usuario basado en el correo electrónico y la contraseña.
   *
   * @param email El correo electrónico del usuario.
   * @param password La contraseña proporcionada por el usuario.
   * @return El usuario autenticado si la validación es exitosa.
   */
  public User authenticate(String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    User user =
        userRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

    // Validar la contraseña utilizando BCryptPasswordEncoder
    if (!passwordValidationService.validatePassword(password, user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid email or password");
    }

    return user; // Usuario autenticado exitosamente
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase();
  }
}
