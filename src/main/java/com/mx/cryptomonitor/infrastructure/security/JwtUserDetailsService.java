package com.mx.cryptomonitor.infrastructure.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.domain.models.Permission;
import com.mx.cryptomonitor.domain.models.Role;
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
        
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority(role.getName())); // Ejemplo: ROLE_ADMIN
            for (Permission permission : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getCode())); // Ejemplo: USER:READ
            }
        }

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPasswordHash(),
            authorities
        );
    }
}