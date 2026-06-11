package com.mx.cryptomonitor.integration.user.infrastructure.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

import jakarta.servlet.http.Cookie;

class TokenControllerIT extends UserModuleIntegrationTest {

  @Test
  void revoke_withValidAccessAndRefresh_thenRefreshMustBeRejected() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/tokens/revoke")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .cookie(refreshCookie(tokens.refreshToken())))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .cookie(refreshCookie(tokens.refreshToken())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refresh_withValidAccessAndRefresh_returns_rotated_tokens() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .cookie(refreshCookie(tokens.refreshToken())))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  @Test
  void revoke_withInvalidRefreshToken_andValidAccess_returns401() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/tokens/revoke")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .cookie(refreshCookie("invalid-refresh-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.detail").value("Authentication is required or token is invalid."));
  }

  @Test
  void refresh_withoutAuthorizationHeader_returns200_whenRefreshTokenIsValid() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(post("/api/v1/tokens/refresh").cookie(refreshCookie(tokens.refreshToken())))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  private Cookie refreshCookie(String refreshToken) {
    return new Cookie("refresh_token", refreshToken);
  }
}
