package com.mx.cryptomonitor.integration.marketdata.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.marketdata.application.port.in.RegisterMergerUseCase;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.CorporateActionAdminController;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.security.CustomAccessDeniedHandler;
import com.mx.cryptomonitor.user.infrastructure.security.JwtAuthenticationEntryPoint;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.user.infrastructure.security.SecurityConfig;
import com.mx.cryptomonitor.user.infrastructure.security.handler.CustomOAuth2FailureHandler;
import com.mx.cryptomonitor.user.infrastructure.security.handler.OAuth2AuthenticationSuccessHandler;
import com.mx.cryptomonitor.user.infrastructure.security.oauth.CustomOAuth2UserService;
import com.mx.cryptomonitor.user.infrastructure.security.oidc.CustomOidcUserService;

/**
 * ADR-0015: valida que {@code POST /api/v1/admin/corporate-actions/merger} está restringido a
 * ADMIN. 401 sin autenticar, 403 con ROLE_USER, 202 con ROLE_ADMIN. Slice de seguridad (sin JWT
 * real, vía {@code @WithMockUser}); el use case va mockeado. Vive en su propio paquete {@code
 * .admin} a propósito: su {@code @SpringBootConfiguration} NO debe eclipsar a los ITs plain
 * {@code @SpringBootTest} de {@code integration.marketdata}.
 */
@SpringBootTest(classes = CorporateActionAdminControllerSecurityIT.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorporateActionAdminControllerSecurityIT {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    SecurityConfig.class,
    JwtAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class,
    CorporateActionAdminController.class
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

  @MockitoBean private RegisterMergerUseCase registerMergerUseCase;

  // Dependencias de SecurityConfig (mockeadas para no escanear infraestructura no relacionada).
  @MockitoBean private JwtUserDetailsService jwtUserDetailsService;
  @MockitoBean private CustomOAuth2FailureHandler customOAuth2FailureHandler;
  @MockitoBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
  @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
  @MockitoBean private CustomOidcUserService customOidcUserService;
  @MockitoBean private PasswordEncoder passwordEncoder;

  private static final String BODY =
      """
      {
        "predecessorSymbol":"CEPT",
        "successorSymbol":"SECZ",
        "mergerType":"ALL_STOCK",
        "exchangeRatio":1.0,
        "currency":"USD",
        "effectiveDate":"2024-03-01"
      }
      """;

  @Test
  void unauthenticatedReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/corporate-actions/merger")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "user@test.com", roles = "USER")
  void userRoleReturns403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/corporate-actions/merger")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "admin@test.com", roles = "ADMIN")
  void adminRoleReturns202() throws Exception {
    when(registerMergerUseCase.registerMerger(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/admin/corporate-actions/merger")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isAccepted());
  }
}
