package com.mx.cryptomonitor.infrastructure.security;

import java.util.ArrayList;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

@Service
public class JwtUserDetailsService implements UserDetailsService {
	
    private final Logger logger = LoggerFactory.getLogger(JwtUserDetailsService.class);


    @Autowired
    private UserRepository userRepository;


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    	
        logger.info("Intentando cargar el usuario con email: {}", email);


        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.error("Usuario no encontrado con email: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        // Convierte el objeto User a un UserDetails válido para Spring Security
        logger.info("Usuario encontrado: {}", user.getEmail());

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPasswordHash(),
            Collections.emptyList() // Agrega roles si son necesarios
        );
    }
}