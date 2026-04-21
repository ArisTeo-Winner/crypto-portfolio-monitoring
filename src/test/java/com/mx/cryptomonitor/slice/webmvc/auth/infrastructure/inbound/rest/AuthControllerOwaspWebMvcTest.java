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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.user.application.dto.request.LoginRequest;
import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.domain.exception.TooManyLoginRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.AuthController;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem.AuthExceptionHandler;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.user.infrastructure.security.LoginRateLimiter;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthExceptionHandler.class)
class AuthControllerOwaspWebMvcTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private AuthService authService;
  @MockBean private JwtRequestFilter jwtRequestFilter;
  @MockBean private LoginRateLimiter loginRateLimiter;

  @Test
  @DisplayName("OWASP API4: POST /api/v1/auth/login rate-limited -> 429 + Retry-After + traceId")
  void login_rateLimited_returns_problem_details_retry_after_and_trace_id() throws Exception {
    doThrow(new TooManyLoginRequestsException(300L)).when(loginRateLimiter).validateOrThrow(any());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .header("X-Request-Id", "req-auth-429")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .content("{\"email\":\"user@example.com\",\"password\":\"ValidPass123!\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "300"))
            .andExpect(jsonPath("$.title").value("Too Many Requests"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"))
            .andExpect(jsonPath("$.errorCode").value("LOGIN_RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.traceId").value("req-auth-429"))
            .andReturn();

    JSONAssert.assertEquals(
        """
        {
          "status": 429,
          "errorCode": "LOGIN_RATE_LIMIT_EXCEEDED",
          "instance": "/api/v1/auth/login",
          "traceId": "req-auth-429"
        }
        """,
        result.getResponse().getContentAsString(),
        false);
  }

  @Test
  @DisplayName("OWASP API2: credenciales invalidas -> 401 generico sin fuga de email/password")
  void login_bad_credentials_does_not_echo_email_or_password() throws Exception {
    when(authService.login(any(LoginRequest.class), any()))
        .thenThrow(new BadCredentialsException("Invalid credentials for user@example.com"));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .content("{\"email\":\"user@example.com\",\"password\":\"SuperSecret!2026\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body).doesNotContain("user@example.com");
    assertThat(body).doesNotContain("SuperSecret!2026");
  }

  @Test
  @DisplayName("OWASP API8: payload invalido -> 400 sin echo de credenciales")
  void login_invalid_payload_returns_400_without_echoing_password() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest("not-an-email", "SuperSecret!2026"))))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain("SuperSecret!2026");
  }

  @Test
  @DisplayName("OWASP API2: login OK devuelve tokens con no-store y sin cookie de auth")
  void login_ok_returns_tokens_without_auth_cookie() throws Exception {
    when(authService.login(any(LoginRequest.class), any()))
        .thenReturn(new JwtResponse("access-owasp", "refresh-owasp"));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest("user@example.com", "ValidPass123!"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-owasp"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-owasp"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
            .andReturn();

    assertThat(result.getResponse().getCookie("jwt")).isNull();
  }
}
