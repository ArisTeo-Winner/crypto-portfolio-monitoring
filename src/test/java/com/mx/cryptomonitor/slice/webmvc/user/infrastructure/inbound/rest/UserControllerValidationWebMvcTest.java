package com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.application.service.UserService;
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
class UserControllerValidationWebMvcTest {

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

  /*
  @Test
  @DisplayName("POST /users/password/reset -> 400 ProblemDetail VALIDATION_ERROR for invalid email")
  void passwordReset_invalidEmail_returns_400_problemDetail() throws Exception {
    PasswordResetRequest request = new PasswordResetRequest("not-an-email");

    mockMvc
        .perform(
            post("/api/v1/users/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("email")));
  }

  @Test
  @DisplayName("POST /users/password/change -> 400 ProblemDetail VALIDATION_ERROR for weak newPassword")
  void passwordChange_weakPassword_returns_400_problemDetail() throws Exception {
    // newPassword missing upper/lower/special/number strength requirements
    String payload = "{\"currentPassword\":\"OldPass123!\",\"newPassword\":\"weakpass\"}";

    mockMvc
        .perform(
            post("/api/v1/users/password/change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("newPassword")));
  }

  @Test
  @DisplayName("POST /users/email/verify -> 400 ProblemDetail VALIDATION_ERROR for blank token")
  void emailVerify_blankToken_returns_400_problemDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("token")));
  }

  @Test
  @DisplayName("PUT /users/me -> 400 ProblemDetail VALIDATION_ERROR for oversize fields")
  void meUpdate_oversizeBio_returns_400_problemDetail() throws Exception {
    String longBio = "x".repeat(501);
    UserMeUpdateRequest request = new UserMeUpdateRequest(null, null, null, null, null, null, null, null, longBio);

    mockMvc
        .perform(
            put("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("bio")));
  }

  @Test
  @DisplayName("POST /users/password/reset -> 500 ProblemDetail INTERNAL_ERROR without leaking exception detail")
  void passwordReset_whenServiceThrows_returns_500_problemDetail_generic() throws Exception {
    doThrow(new RuntimeException("db is down"))
        .when(userService)
        .requestPasswordReset("user@example.com");

    PasswordResetRequest request = new PasswordResetRequest("user@example.com");

    mockMvc
        .perform(
            post("/api/v1/users/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("db is down"))));
  }
  */
}
