package com.mx.cryptomonitor.slice.webmvc.auth.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.AuthController;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem.AuthExceptionHandler;
import com.mx.cryptomonitor.user.infrastructure.security.CustomAccessDeniedHandler;
import com.mx.cryptomonitor.user.infrastructure.security.JwtAuthenticationEntryPoint;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.user.infrastructure.security.LoginRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.security.RefreshTokenCookieHelper;
import com.mx.cryptomonitor.user.infrastructure.security.SecurityConfig;
import com.mx.cryptomonitor.user.infrastructure.security.handler.CustomOAuth2FailureHandler;
import com.mx.cryptomonitor.user.infrastructure.security.handler.OAuth2AuthenticationSuccessHandler;
import com.mx.cryptomonitor.user.infrastructure.security.oauth.CustomOAuth2UserService;
import com.mx.cryptomonitor.user.infrastructure.security.oidc.CustomOidcUserService;

/**
 * Verifica que el SecurityFilterChain real no emite redirects (3xx) en los endpoints públicos de
 * autenticación y que los métodos HTTP no permitidos devuelven 405 — no 3xx.
 *
 * <p>Estos tests tienen los filtros de Spring Security ACTIVOS ({@code addFilters = true} es el
 * default en {@code @SpringBootTest}). Los tests de slice {@code @WebMvcTest} con {@code addFilters
 * = false} no pueden detectar este tipo de regresión.
 */
@SpringBootTest(classes = SecurityHttpRoutingTest.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHttpRoutingTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    SecurityConfig.class,
    JwtAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class,
    AuthController.class,
    AuthExceptionHandler.class,
  })
  static class TestApp {

    @Bean
    JwtRequestFilter jwtRequestFilter() {
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

  // AuthController deps
  @MockBean private AuthService authService;
  @MockBean private LoginRateLimiter loginRateLimiter;
  @MockBean private RefreshTokenCookieHelper refreshTokenCookieHelper;

  // SecurityConfig required beans
  @MockBean private JwtUserDetailsService jwtUserDetailsService;
  @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
  @MockBean private CustomOAuth2FailureHandler customOAuth2FailureHandler;
  @MockBean private CustomOAuth2UserService customOAuth2UserService;
  @MockBean private CustomOidcUserService customOidcUserService;
  @MockBean private PasswordEncoder passwordEncoder;

  // ── Redirect tests ────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /api/v1/auth/login nunca emite redirect 3xx (seguridad activa)")
  void login_post_never_redirects() throws Exception {
    // Spring Security no debe redirigir este endpoint público.
    // Si se detecta un 3xx el cliente HTTP lo seguiría como GET → 405.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"ValidPass123!\"}"))
        .andExpect(
            status()
                .is(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.both(org.hamcrest.Matchers.greaterThanOrEqualTo(300))
                            .and(org.hamcrest.Matchers.lessThan(400)))));
  }

  @Test
  @DisplayName("POST /api/v1/auth/login no redirige a /login (OAuth2 loginPage no interfiere)")
  void login_post_does_not_redirect_to_oauth2_login_page() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"ValidPass123!\"}"))
        .andExpect(header().doesNotExist("Location"));
  }

  @Test
  @DisplayName("POST /api/v1/tokens/refresh no redirige (endpoint público con filtros activos)")
  void token_refresh_post_never_redirects() throws Exception {
    mockMvc.perform(post("/api/v1/tokens/refresh")).andExpect(header().doesNotExist("Location"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/logout no redirige (endpoint público con filtros activos)")
  void logout_post_never_redirects() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(header().doesNotExist("Location"));
  }

  // ── HTTP method tests ─────────────────────────────────────────────────────

  @Test
  @DisplayName("GET /api/v1/auth/login devuelve 405 — nunca 3xx que confunda al cliente")
  void login_get_returns_405_not_redirect() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/login").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  @DisplayName("DELETE /api/v1/auth/login devuelve 405")
  void login_delete_returns_405() throws Exception {
    mockMvc.perform(delete("/api/v1/auth/login")).andExpect(status().isMethodNotAllowed());
  }

  // ── Endpoints protegidos sin token → 401 (no redirect) ───────────────────

  @Test
  @DisplayName("Endpoint protegido sin JWT → 401 application/problem+json, no redirect")
  void protected_endpoint_without_token_returns_401_not_redirect() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/sessions").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Location"));
  }
}
