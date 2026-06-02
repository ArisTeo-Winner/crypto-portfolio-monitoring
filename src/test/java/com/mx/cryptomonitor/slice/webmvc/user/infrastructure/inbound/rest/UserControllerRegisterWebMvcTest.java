package com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.application.dto.response.UserResponse;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.application.service.UserService;
import com.mx.cryptomonitor.user.domain.exception.TooManyRegistrationRequestsException;
import com.mx.cryptomonitor.user.domain.exception.UserRegistrationConflictException;
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
import com.mx.cryptomonitor.user.infrastructure.security.LoginRateLimiter;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserExceptionHandler.class)
@Tag("security")
class UserControllerRegisterWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserService userService;
  @MockBean private AuthService authService;
  @MockBean private JwtRequestFilter jwtRequestFilter;
  @MockBean private LoginRateLimiter loginRateLimiter;
  @MockBean private UserRegistrationRateLimiter userRegistrationRateLimiter;
  @MockBean private PasswordResetRateLimiter passwordResetRateLimiter;
  @MockBean private PasswordChangeRateLimiter passwordChangeRateLimiter;
  @MockBean private EmailVerifyRateLimiter emailVerifyRateLimiter;
  @MockBean private MeReadRateLimiter meReadRateLimiter;
  @MockBean private MeWriteRateLimiter meWriteRateLimiter;
  @MockBean private MeDeleteRateLimiter meDeleteRateLimiter;

  @Test
  @DisplayName("201 Created - Registro exitoso sin exponer password")
  void shouldRegisterUserSuccessfully() throws Exception {
    UserRegistrationRequest validRequest =
        new UserRegistrationRequest(
            "secure_user",
            "secure@example.com",
            "StrongP@ssw0rd!2026",
            "John",
            "Doe",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    UserResponse mockResponse =
        new UserResponse(
            "secure_user",
            "secure@example.com",
            "John",
            "Doe",
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
            null,
            null);

    when(userService.registerUser(any(UserRegistrationRequest.class))).thenReturn(mockResponse);

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("secure@example.com"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.username").value("secure_user"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"' OR '1'='1", "<script>alert(1)</script>", "admin--", "union select 1,2,3"})
  @DisplayName("400 Bad Request - Rechazar payload con intentos de Injection/XSS en username")
  void shouldRejectMaliciousUsernameInput(String maliciousString) throws Exception {
    UserRegistrationRequest maliciousRequest =
        new UserRegistrationRequest(
            maliciousString,
            "test@example.com",
            "StrongP@ssw0rd!2026",
            "John",
            "Doe",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maliciousRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"123456", "password", "qwerty", "admin123", "short"})
  @DisplayName("400 Bad Request - Rechazar contrasenas debiles")
  void shouldRejectWeakPasswords(String weakPass) throws Exception {
    UserRegistrationRequest weakPassRequest =
        new UserRegistrationRequest(
            "valid_user",
            "valid@example.com",
            weakPass,
            "John",
            "Doe",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(weakPassRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("409 Conflict - Conflicto de registro sin filtrar si existe email o username")
  void shouldReturnConflictWhenRegistrationAlreadyExists() throws Exception {
    UserRegistrationRequest duplicateRequest =
        new UserRegistrationRequest(
            "dup_user",
            "dup@example.com",
            "StrongP@ssw0rd!2026",
            "Jane",
            "Doe",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(userService.registerUser(any(UserRegistrationRequest.class)))
        .thenThrow(new UserRegistrationConflictException("Registration conflict"));

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("REGISTRATION_CONFLICT"))
        .andExpect(
            jsonPath("$.detail").value("Registration cannot be completed with the provided data."));
  }

  @Test
  @DisplayName("500 Internal Server Error - Error inesperado controlado")
  void shouldReturnControlledInternalServerError() throws Exception {
    UserRegistrationRequest request =
        new UserRegistrationRequest(
            "boom_user",
            "boom@example.com",
            "StrongP@ssw0rd!2026",
            "Boom",
            "User",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(userService.registerUser(any(UserRegistrationRequest.class)))
        .thenThrow(new RuntimeException("DB leak should never be exposed"));

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
  }

  @Test
  @DisplayName("429 Too Many Requests - Rate limit con Retry-After")
  void shouldReturnTooManyRequestsWhenRateLimitIsExceeded() throws Exception {
    UserRegistrationRequest request =
        new UserRegistrationRequest(
            "rate_user",
            "rate@example.com",
            "StrongP@ssw0rd!2026",
            "Rate",
            "Limited",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    doThrow(new TooManyRegistrationRequestsException(30L))
        .when(userRegistrationRateLimiter)
        .validateOrThrow(any());

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.detail").value("Too many requests. Please try again later."))
        .andExpect(jsonPath("$.title").value("Too Many Requests"))
        .andExpect(header().string("Retry-After", "30"));
  }
}
