package com.mx.cryptomonitor.user.application.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.user.domain.model.Permission;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@Service
public class JwtUserDetailsService implements UserDetailsService {

  private final Logger logger = LoggerFactory.getLogger(JwtUserDetailsService.class);
  private final UserRepository userRepository;

  public JwtUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    String normalizedEmail = normalizeEmail(email);
    logger.info("Intentando cargar el usuario con email: {}", normalizedEmail);

    User user =
        userRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(
                () -> {
                  logger.error("Usuario no encontrado con email: {}", normalizedEmail);
                  return new UsernameNotFoundException(
                      "User not found with email: " + normalizedEmail);
                });

    logger.info("Usuario encontrado: {}", user.getEmail());

    List<GrantedAuthority> authorities = new ArrayList<>();
    for (Role role : user.getRoles()) {
      authorities.add(new SimpleGrantedAuthority(role.getName()));
      for (Permission permission : role.getPermissions()) {
        authorities.add(new SimpleGrantedAuthority(permission.getCode()));
      }
    }

    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
        .authorities(authorities)
        .accountExpired(false)
        .accountLocked(false)
        .credentialsExpired(false)
        .disabled(!user.isActive())
        .build();
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase();
  }
}
