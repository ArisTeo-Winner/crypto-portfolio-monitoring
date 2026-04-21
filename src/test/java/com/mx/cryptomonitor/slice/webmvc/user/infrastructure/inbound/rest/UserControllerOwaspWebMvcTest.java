package com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.application.dto.response.UserResponse;
import com.mx.cryptomonitor.user.application.service.UserService;
import com.mx.cryptomonitor.user.domain.exception.TooManyRegistrationRequestsException;
import com.mx.cryptomonitor.user.domain.model.User;
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
class UserControllerOwaspWebMvcTest {

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

  @Test
  @DisplayName("RFC9457: 400 en registro invalido, con traceId heredado de X-Request-Id")
  void registerValidationErrorShouldFollowProblemDetailsContract() throws Exception {
    UserRegistrationRequest invalidRequest =
        new UserRegistrationRequest(
            "x", "invalid-email", "weak", "John", "Doe", null, null, null, null, null, null, null);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", "req-owasp-100")
                    .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Validation Error"))
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value("req-owasp-100"))
            .andExpect(jsonPath("$.errors").isArray())
            .andReturn();

    JSONAssert.assertEquals(
        """
        {
          "status": 400,
          "detail": "One or more fields are invalid.",
          "instance": "/api/v1/users/register",
          "errorCode": "VALIDATION_ERROR"
        }
        """,
        result.getResponse().getContentAsString(),
        false);
    assertThat(result.getResponse().getContentAsString()).doesNotContain("weak");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"' OR '1'='1", "<script>alert(1)</script>", "admin--", "union select 1,2"})
  @DisplayName("OWASP API8/API3: registro rechaza payload malicioso en username")
  void registerShouldRejectMaliciousUsername(String maliciousUsername) throws Exception {
    UserRegistrationRequest maliciousRequest =
        new UserRegistrationRequest(
            maliciousUsername,
            "safe@example.com",
            "StrongP@ssw0rd!2026",
            "John",
            "Doe",
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

  @Test
  @DisplayName("OWASP API4: 429 en rate limit de registro con Retry-After")
  void registerShouldReturn429WithRetryAfterWhenRateLimited() throws Exception {
    UserRegistrationRequest request =
        new UserRegistrationRequest(
            "rate_user",
            "rate@example.com",
            "StrongP@ssw0rd!2026",
            "Rate",
            "User",
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    doThrow(new TooManyRegistrationRequestsException(45L))
        .when(userRegistrationRateLimiter)
        .validateOrThrow(any());

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "45"))
        .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
  }

  @Test
  @DisplayName("200 en getUserByEmail con salida sanitizada (sin passwordHash)")
  void getUserByEmailShouldReturnSanitizedResponse() throws Exception {
    UserResponse response =
        new UserResponse(
            "safe_user",
            "safe@example.com",
            "Safe",
            "User",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            LocalDateTime.now());
    when(userService.findByEmail("safe@example.com")).thenReturn(Optional.of(response));

    mockMvc
        .perform(get("/api/v1/users/safe@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("safe@example.com"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  @DisplayName("404 en getUserByEmail cuando usuario no existe")
  void getUserByEmailShouldReturn404WhenNotFound() throws Exception {
    when(userService.findByEmail("missing@example.com")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/users/missing@example.com"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(""));
  }

  @Test
  @DisplayName("404 en delete cuando userService reporta ID inexistente")
  void deleteUserShouldReturn404WhenUserDoesNotExist() throws Exception {
    UUID userId = UUID.randomUUID();
    doThrow(new IllegalArgumentException("User does not exist"))
        .when(userService)
        .deleteUserById(userId);

    mockMvc
        .perform(delete("/api/v1/users/{id}", userId))
        .andExpect(status().isNotFound())
        .andExpect(content().string("User does not exist"));
  }

  @Test
  @DisplayName("500 controlado en delete sin fuga de detalle tecnico")
  void deleteUserShouldReturnGeneric500WithoutLeak() throws Exception {
    UUID userId = UUID.randomUUID();
    doThrow(new RuntimeException("SQL syntax near password_hash"))
        .when(userService)
        .deleteUserById(userId);

    MvcResult result =
        mockMvc
            .perform(delete("/api/v1/users/{id}", userId))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string("Error interno del servidor."))
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");
  }

  @Test
  @DisplayName("400 en update profile cuando email es vacio")
  void updateProfileShouldReturn400WhenEmailIsBlank() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/users/profile")
                .param("email", " ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(""));
  }

  @Test
  @DisplayName("OWASP API3: update profile no debe exponer passwordHash en response")
  void updateProfileMustNotExposePasswordHash() throws Exception {
    User updated =
        User.builder()
            .username("safe_user")
            .email("safe@example.com")
            .passwordHash("$2a$10$Awdt8RblYhQj4PeNgvYf5ep6o.0F5.6jD1t5jT4QrcvO0YVxN3m3m")
            .firstName("Safe")
            .build();

    when(userService.updateUser(eq("safe@example.com"), any(User.class))).thenReturn(updated);

    mockMvc
        .perform(
            put("/api/v1/users/profile")
                .param("email", "safe@example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Safe\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }
}
