package com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserExceptionHandler.class)
class UserControllerInventoryWebMvcTest {

  @Autowired private MockMvc mockMvc;

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
  void usersLoginEndpointMustNotExist_anymore() throws Exception {
    // "/api/v1/users/{email}" exists, so "/api/v1/users/login" can still match that route.
    // The important guarantee is: there is no POST handler anymore for this path.
    mockMvc.perform(post("/api/v1/users/login")).andExpect(status().isMethodNotAllowed());
  }
}
