package com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
import com.mx.cryptomonitor.user.application.service.TokenService;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;
import com.mx.cryptomonitor.user.domain.exception.UserNotFoundException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.TokenController;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem.AuthExceptionHandler;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;

@WebMvcTest(TokenController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthExceptionHandler.class)
class TokenControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TokenService tokenService;

  @MockBean private JwtRequestFilter jwtRequestFilter;

  @Test
  void revokeRefreshToken_ok_returns_204() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tokens/revoke")
                .header("Authorization", "Bearer access-valid-token")
                .header("X-Refresh-Token", "refresh-valid-token"))
        .andExpect(status().isNoContent());

    verify(tokenService).revokeRefreshToken("refresh-valid-token");
  }

  @Test
  void revokeRefreshToken_invalidToken_returns_401_problem_detail() throws Exception {
    doThrow(new SecurityException("Refresh token no encontrado"))
        .when(tokenService)
        .revokeRefreshToken("refresh-invalid-token");

    mockMvc
        .perform(
            post("/api/v1/tokens/revoke")
                .header("Authorization", "Bearer access-valid-token")
                .header("X-Refresh-Token", "refresh-invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.detail").value("Authentication is required or token is invalid."));
  }

  @Test
  void revokeRefreshToken_missingRefreshHeader_returns_400() throws Exception {
    mockMvc
        .perform(post("/api/v1/tokens/revoke").header("Authorization", "Bearer access-valid-token"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refreshToken_ok_returns_200_with_rotated_tokens() throws Exception {
    when(tokenService.refreshToken("refresh-valid-token"))
        .thenReturn(new JwtResponse("new-access-token", "new-refresh-token"));

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .header("Authorization", "Bearer access-valid-token")
                .header("X-Refresh-Token", "refresh-valid-token"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.accessToken").value("new-access-token"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));

    verify(tokenService).refreshToken("refresh-valid-token");
  }

  @Test
  void refreshToken_invalidToken_returns_401_problem_detail() throws Exception {
    when(tokenService.refreshToken("invalid-refresh-token"))
        .thenThrow(new InvalidTokenException("Token de refresco invalido"));

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .header("Authorization", "Bearer access-valid-token")
                .header("X-Refresh-Token", "invalid-refresh-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.detail").value("Authentication is required or token is invalid."));
  }

  @Test
  void refreshToken_userDeleted_returns_401_problem_detail() throws Exception {
    when(tokenService.refreshToken("refresh-from-deleted-user"))
        .thenThrow(new UserNotFoundException("Usuario no encontrado"));

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .header("Authorization", "Bearer access-valid-token")
                .header("X-Refresh-Token", "refresh-from-deleted-user"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.detail").value("Authentication is required or token is invalid."));
  }

  @Test
  void refreshToken_missingRefreshHeader_returns_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tokens/refresh").header("Authorization", "Bearer access-valid-token"))
        .andExpect(status().isBadRequest());
  }
}
