package com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.user.application.dto.request.EmailVerifyRequest;
import com.mx.cryptomonitor.user.application.dto.request.PasswordChangeRequest;
import com.mx.cryptomonitor.user.application.dto.request.PasswordResetRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserMeUpdateRequest;
import com.mx.cryptomonitor.user.application.dto.response.UserResponse;
import com.mx.cryptomonitor.user.application.service.UserService;
import com.mx.cryptomonitor.user.domain.exception.TooManyEmailVerifyRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyMeDeleteRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyMeReadRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyMeWriteRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyPasswordChangeRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyPasswordResetRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyRegistrationRequestsException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.UserController;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem.UserExceptionHandler;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.EmailVerifyRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeDeleteRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeReadRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeWriteRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.PasswordChangeRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.PasswordResetRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.UserRegistrationRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserExceptionHandler.class)
class UserControllerRateLimitAllEndpointsWebMvcTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserService userService;
  @MockBean private JwtRequestFilter jwtRequestFilter;

  @MockBean private UserRegistrationRateLimiter userRegistrationRateLimiter;
  @MockBean private PasswordResetRateLimiter passwordResetRateLimiter;
  @MockBean private PasswordChangeRateLimiter passwordChangeRateLimiter;
  @MockBean private EmailVerifyRateLimiter emailVerifyRateLimiter;
  @MockBean private MeReadRateLimiter meReadRateLimiter;
  @MockBean private MeWriteRateLimiter meWriteRateLimiter;
  @MockBean private MeDeleteRateLimiter meDeleteRateLimiter;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("POST /users/register -> 429 + Retry-After (5 req/10 min)")
  void register_rateLimited_returns_429_retryAfter() throws Exception {
    doThrow(new TooManyRegistrationRequestsException(60L))
        .when(userRegistrationRateLimiter)
        .validateOrThrow(any());

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username":"u_1234",
                      "email":"u@example.com",
                      "password":"StrongP@ssw0rd!2026",
                      "firstName":"A",
                      "lastName":"B"
                    }
                    """))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
  }

  @Test
  @DisplayName("POST /users/password/reset -> 429 + Retry-After (3 req/15 min)")
  void passwordReset_rateLimited_returns_429_retryAfter() throws Exception {
    doThrow(new TooManyPasswordResetRequestsException(120L))
        .when(passwordResetRateLimiter)
        .validateOrThrow(any());

    PasswordResetRequest request = new PasswordResetRequest("user@example.com");

    mockMvc
        .perform(
            post("/api/v1/users/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "120"))
        .andExpect(jsonPath("$.errorCode").value("PASSWORD_RESET_RATE_LIMIT_EXCEEDED"));
  }

  @Test
  @DisplayName("POST /users/password/change -> 429 + Retry-After (5 req/10 min)")
  void passwordChange_rateLimited_returns_429_retryAfter() throws Exception {
    doThrow(new TooManyPasswordChangeRequestsException(30L))
        .when(passwordChangeRateLimiter)
        .validateOrThrow(any());

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user@example.com", "N/A"));

    PasswordChangeRequest request = new PasswordChangeRequest("OldPass123!", "NewStrongP@ss1!");

    mockMvc
        .perform(
            post("/api/v1/users/password/change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "30"))
        .andExpect(jsonPath("$.errorCode").value("PASSWORD_CHANGE_RATE_LIMIT_EXCEEDED"));
  }

  @Test
  @DisplayName("POST /users/email/verify -> 429 + Retry-After (5 req/10 min)")
  void emailVerify_rateLimited_returns_429_retryAfter() throws Exception {
    doThrow(new TooManyEmailVerifyRequestsException(30L))
        .when(emailVerifyRateLimiter)
        .validateOrThrow(any());

    EmailVerifyRequest request = new EmailVerifyRequest("token-abc");

    mockMvc
        .perform(
            post("/api/v1/users/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "30"))
        .andExpect(jsonPath("$.errorCode").value("EMAIL_VERIFY_RATE_LIMIT_EXCEEDED"));
  }

  @Test
  @DisplayName("GET /users/me -> 429 (60 req/1 min) soft limit (sin Retry-After)")
  void meGet_softRateLimited_returns_429_without_retryAfter() throws Exception {
    doThrow(new TooManyMeReadRequestsException()).when(meReadRateLimiter).validateOrThrow(any());

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user@example.com", "N/A"));

    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().doesNotExist("Retry-After"))
        .andExpect(jsonPath("$.errorCode").value("ME_READ_RATE_LIMIT_EXCEEDED"));
  }

  @Test
  @DisplayName("PUT /users/me -> 429 (20 req/1 min) soft limit (sin Retry-After)")
  void mePut_softRateLimited_returns_429_without_retryAfter() throws Exception {
    doThrow(new TooManyMeWriteRequestsException()).when(meWriteRateLimiter).validateOrThrow(any());

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user@example.com", "N/A"));

    UserMeUpdateRequest request =
        new UserMeUpdateRequest("A", "B", null, null, null, null, null, null, null, null, null);

    mockMvc
        .perform(
            put("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().doesNotExist("Retry-After"))
        .andExpect(jsonPath("$.errorCode").value("ME_WRITE_RATE_LIMIT_EXCEEDED"));
  }

  @Test
  @DisplayName("DELETE /users/me -> 429 + Retry-After + admin alert (2 req/1 hora)")
  void meDelete_rateLimited_returns_429_retryAfter() throws Exception {
    doThrow(new TooManyMeDeleteRequestsException(3600L))
        .when(meDeleteRateLimiter)
        .validateOrThrow(any());

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user@example.com", "N/A"));

    mockMvc
        .perform(delete("/api/v1/users/me"))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "3600"))
        .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DELETION_RATE_LIMIT_EXCEEDED"));
  }

  @Test
  @DisplayName("GET /users/me -> 200 debe retornar UserResponse sin passwordHash")
  void meGet_ok_returns_sanitized_profile() throws Exception {
    org.mockito.Mockito.when(userService.me("user@example.com"))
        .thenReturn(
            new UserResponse(
                "u",
                "user@example.com",
                "A",
                "B",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                LocalDateTime.now(),
                null,
                null));

    mockMvc
        .perform(
            get("/api/v1/users/me")
                // With addFilters=false we must provide a Principal; relying on
                // SecurityContextHolder
                // alone leaves the MVC Authentication argument null and triggers a 400.
                .principal(new UsernamePasswordAuthenticationToken("user@example.com", "N/A")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }
}
