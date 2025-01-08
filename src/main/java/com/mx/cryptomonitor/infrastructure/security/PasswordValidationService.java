package com.mx.cryptomonitor.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordValidationService {

	private final PasswordEncoder passwordEncoder;

    public PasswordValidationService() {
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Valida la contraseña sin procesar (input del usuario) contra la contraseña codificada (almacenada en la base de datos).
     *
     * @param rawPassword    La contraseña ingresada por el usuario.
     * @param encodedPassword La contraseña codificada almacenada en la base de datos.
     * @return true si la contraseña es válida; false en caso contrario.
     */
    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
