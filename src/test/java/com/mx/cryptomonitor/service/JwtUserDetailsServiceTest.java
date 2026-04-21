package com.mx.cryptomonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.domain.model.Permission;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class JwtUserDetailsServiceTest {

  @Mock private UserRepository userRepository;

  @Test
  void loadUserByUsername_should_lookup_email_case_insensitively() {
    Permission permission =
        Permission.builder().code("portfolio:read").name("Portfolio Read").build();
    Role role = Role.builder().name("ROLE_USER").permissions(Set.of(permission)).build();
    User user =
        User.builder()
            .email("ortiz_aristeo@hotmail.com")
            .username("ortiz12")
            .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
            .roles(List.of(role))
            .active(true)
            .build();

    when(userRepository.findByEmailIgnoreCase("ortiz_aristeo@hotmail.com"))
        .thenReturn(Optional.of(user));

    JwtUserDetailsService service = new JwtUserDetailsService(userRepository);
    UserDetails details = service.loadUserByUsername("Ortiz_Aristeo@Hotmail.com");

    assertThat(details.getUsername()).isEqualTo("ortiz_aristeo@hotmail.com");
    assertThat(details.isEnabled()).isTrue();
    assertThat(details.getAuthorities())
        .extracting("authority")
        .contains("ROLE_USER", "portfolio:read");
    verify(userRepository).findByEmailIgnoreCase("ortiz_aristeo@hotmail.com");
  }
}
