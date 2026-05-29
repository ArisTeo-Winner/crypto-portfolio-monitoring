package com.mx.cryptomonitor.slice.webmvc.auth.infrastructure.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.user.application.dto.request.LoginRequest;
import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.domain.exception.AuthenticationException;
import com.mx.cryptomonitor.user.domain.exception.TooManyLoginRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.AuthController;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem.AuthExceptionHandler;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.user.infrastructure.security.LoginRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.security.RefreshTokenCookieHelper;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthExceptionHandler.class)
class AuthControllerLoginWebMvcTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private AuthService authService;
  @MockBean private JwtRequestFilter jwtRequestFilter;
  @MockBean private LoginRateLimiter loginRateLimiter;
  @MockBean private RefreshTokenCookieHelper cookieHelper;

  @Test
  void login_ok_returns_accessToken_in_body_and_refreshToken_as_httpOnly_cookie() throws Exception {
    when(authService.login(any(LoginRequest.class), any()))
        .thenReturn(new AuthResult("access-123", "refresh-456"));
    when(cookieHelper.buildSetCookieHeader("refresh-456"))
        .thenReturn("refresh_token=refresh-456; Path=/api/v1/; HttpOnly; SameSite=Strict");
    when(cookieHelper.headerName()).thenReturn("Set-Cookie");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest("user@example.com", "ValidPass123!"))))
            .andExpect(status().isOk())
            // accessToken en el body — el frontend lo guarda en memoria
            .andExpect(jsonPath("$.accessToken").value("access-123"))
            // refreshToken NO debe aparecer en el body
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            // Anti-caché obligatorio
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Pragma", "no-cache"))
            // Cookie HttpOnly con el refresh token
            .andExpect(
                header()
                    .string(
                        "Set-Cookie",
                        org.hamcrest.Matchers.containsString("refresh_token=refresh-456")))
            .andExpect(
                header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
            .andReturn();

    // El refresh token no viaja en ninguna cookie llamada "jwt"
    assertThat(result.getResponse().getCookie("jwt")).isNull();
  }

  @Test
  void login_bad_credentials_returns_401_with_generic_error_contract() throws Exception {
    when(authService.login(any(LoginRequest.class), any()))
        .thenThrow(new BadCredentialsException("Invalid email or password"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"WrongPass!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void login_application_authentication_exception_returns_401_with_generic_error_contract()
      throws Exception {
    when(authService.login(any(LoginRequest.class), any()))
        .thenThrow(new AuthenticationException("Credenciales invalidas"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"WrongPass!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void login_invalid_payload_returns_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void login_rate_limited_returns_429() throws Exception {
    doThrow(new TooManyLoginRequestsException(300L)).when(loginRateLimiter).validateOrThrow(any());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"ValidPass123!\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "300"))
        .andExpect(jsonPath("$.title").value("Too Many Requests"))
        .andExpect(jsonPath("$.errorCode").value("LOGIN_RATE_LIMIT_EXCEEDED"));
  }

  @Test
  void login_unexpected_error_returns_500_without_sensitive_data() throws Exception {
    when(authService.login(any(LoginRequest.class), any()))
        .thenThrow(new RuntimeException("db down"));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"user@example.com\",\"password\":\"ValidPass123!\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain("ValidPass123!");
  }
}
