package com.mx.cryptomonitor.integration.user.infrastructure.inbound.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.user.application.dto.response.UserResponse;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.application.service.UserService;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.UserController;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem.UserExceptionHandler;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.EmailVerifyRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeDeleteRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeReadRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeWriteRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.PasswordChangeRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.PasswordResetRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.UserRegistrationRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.security.CustomAccessDeniedHandler;
import com.mx.cryptomonitor.user.infrastructure.security.JwtAuthenticationEntryPoint;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.user.infrastructure.security.LoginRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.security.SecurityConfig;
import com.mx.cryptomonitor.user.infrastructure.security.handler.OAuth2AuthenticationSuccessHandler;
import com.mx.cryptomonitor.user.infrastructure.security.oauth.CustomOAuth2UserService;
import com.mx.cryptomonitor.user.infrastructure.security.oidc.CustomOidcUserService;

@SpringBootTest(classes = UserControllerAccessControlSecurityIT.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerAccessControlSecurityIT {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    SecurityConfig.class,
    JwtAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class,
    UserController.class,
    UserExceptionHandler.class
  })
  static class TestApp {
    @Bean
    JwtRequestFilter jwtRequestFilter() {
      // No Authorization header is provided in these tests. The filter will just continue the
      // chain.
      return new JwtRequestFilter();
    }

    @Bean
    UserRepository userRepository() {
      return Mockito.mock(UserRepository.class);
    }

    @Bean
    UserDetailsService userDetailsService() {
      return Mockito.mock(UserDetailsService.class);
    }

    @Bean
    JwtTokenUtil jwtTokenUtil() {
      return Mockito.mock(JwtTokenUtil.class);
    }

    @Bean
    SessionRepository sessionRepository() {
      return Mockito.mock(SessionRepository.class);
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private UserService userService;
  @MockBean private AuthService authService;
  @MockBean private LoginRateLimiter loginRateLimiter;
  @MockBean private UserRegistrationRateLimiter userRegistrationRateLimiter;
  @MockBean private PasswordResetRateLimiter passwordResetRateLimiter;
  @MockBean private PasswordChangeRateLimiter passwordChangeRateLimiter;
  @MockBean private EmailVerifyRateLimiter emailVerifyRateLimiter;
  @MockBean private MeReadRateLimiter meReadRateLimiter;
  @MockBean private MeWriteRateLimiter meWriteRateLimiter;
  @MockBean private MeDeleteRateLimiter meDeleteRateLimiter;

  // SecurityConfig required dependencies (mocked to avoid scanning unrelated infrastructure).
  @MockBean private JwtUserDetailsService jwtUserDetailsService;
  @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
  @MockBean private CustomOAuth2UserService customOAuth2UserService;
  @MockBean private CustomOidcUserService customOidcUserService;
  @MockBean private PasswordEncoder passwordEncoder;

  @Test
  void getAllUsersWithoutAuthShouldReturn401ProblemDetails() throws Exception {
    mockMvc
        .perform(get("/api/v1/users").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.title").value("Unauthorized"));
  }

  @Test
  @WithMockUser(username = "user@test.com", roles = "USER")
  void getAllUsersWithUserRoleShouldReturn403() throws Exception {
    mockMvc
        .perform(get("/api/v1/users").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
        .andExpect(jsonPath("$.title").value("Forbidden"));
  }

  @Test
  @WithMockUser(username = "admin@test.com", roles = "ADMIN")
  void getAllUsersWithAdminRoleShouldReturn200() throws Exception {
    when(userService.getAllUsers())
        .thenReturn(
            List.of(
                new UserResponse(
                    "admin_user",
                    "admin@test.com",
                    "Admin",
                    "User",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    true,
                    LocalDateTime.now())));

    mockMvc
        .perform(get("/api/v1/users").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("admin@test.com"));
  }
}
