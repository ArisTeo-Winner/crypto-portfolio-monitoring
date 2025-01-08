package com.mx.cryptomonitor.infrastructure.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

@Service
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordValidationService passwordValidationService;

    /**
     * Autentica al usuario basado en el correo electrónico y la contraseña.
     *
     * @param email    El correo electrónico del usuario.
     * @param password La contraseña proporcionada por el usuario.
     * @return El usuario autenticado si la validación es exitosa.
     */
    public User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Validar la contraseña utilizando BCryptPasswordEncoder
        if (!passwordValidationService.validatePassword(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return user; // Usuario autenticado exitosamente
    }
}