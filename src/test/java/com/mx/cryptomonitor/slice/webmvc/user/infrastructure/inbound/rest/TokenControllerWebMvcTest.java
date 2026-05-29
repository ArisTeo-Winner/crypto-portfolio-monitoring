package com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.application.service.TokenService;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;
import com.mx.cryptomonitor.user.domain.exception.UserNotFoundException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.TokenController;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem.AuthExceptionHandler;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.user.infrastructure.security.RefreshTokenCookieHelper;

import jakarta.servlet.http.Cookie;

@WebMvcTest(TokenController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthExceptionHandler.class)
class TokenControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TokenService tokenService;
  @MockBean private JwtRequestFilter jwtRequestFilter;
  @MockBean private RefreshTokenCookieHelper cookieHelper;

  @BeforeEach
  void setupCookieHelper() {
    when(cookieHelper.headerName()).thenReturn("Set-Cookie");
    when(cookieHelper.extractFromRequest(any()))
        .thenAnswer(
            inv -> {
              jakarta.servlet.http.HttpServletRequest req = inv.getArgument(0);
              Cookie[] cookies = req.getCookies();
              if (cookies == null) return null;
              for (Cookie c : cookies) {
                if ("refresh_token".equals(c.getName())) return c.getValue();
              }
              return null;
            });
  }

  // ── /tokens/revoke ─────────────────────────────────────────────────────────

  @Test
  void revokeRefreshToken_ok_returns_204_and_clears_cookie() throws Exception {
    when(cookieHelper.buildClearCookieHeader())
        .thenReturn("refresh_token=; Max-Age=0; Path=/api/v1/; HttpOnly");

    mockMvc
        .perform(
            post("/api/v1/tokens/revoke")
                .cookie(new Cookie("refresh_token", "refresh-valid-token")))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

    verify(tokenService).revokeRefreshToken("refresh-valid-token");
  }

  @Test
  void revokeRefreshToken_missingCookie_returns_401() throws Exception {
    // Sin cookie → sesión ausente, semánticamente no autenticado
    mockMvc.perform(post("/api/v1/tokens/revoke")).andExpect(status().isUnauthorized());

    verify(tokenService, never()).revokeRefreshToken(any());
  }

  @Test
  void revokeRefreshToken_invalidToken_returns_401() throws Exception {
    doThrow(new SecurityException("Refresh token no encontrado"))
        .when(tokenService)
        .revokeRefreshToken("refresh-invalid-token");

    mockMvc
        .perform(
            post("/api/v1/tokens/revoke")
                .cookie(new Cookie("refresh_token", "refresh-invalid-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  // ── /tokens/refresh ────────────────────────────────────────────────────────

  @Test
  void refreshToken_ok_rotates_cookie_and_returns_new_accessToken() throws Exception {
    when(tokenService.refreshToken(eq("refresh-valid-token"), any()))
        .thenReturn(new AuthResult("new-access-token", "new-refresh-token"));
    when(cookieHelper.buildSetCookieHeader("new-refresh-token"))
        .thenReturn("refresh_token=new-refresh-token; Path=/api/v1/; HttpOnly; SameSite=Strict");

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .cookie(new Cookie("refresh_token", "refresh-valid-token")))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // accessToken en el body
        .andExpect(jsonPath("$.accessToken").value("new-access-token"))
        // refreshToken NO en el body
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        // nueva cookie rotada
        .andExpect(header().string("Set-Cookie", containsString("refresh_token=new-refresh-token")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));

    verify(tokenService).refreshToken(eq("refresh-valid-token"), any());
  }

  @Test
  void refreshToken_missingCookie_returns_401() throws Exception {
    // Sin cookie → sesión ausente, semánticamente no autenticado
    mockMvc.perform(post("/api/v1/tokens/refresh")).andExpect(status().isUnauthorized());

    verify(tokenService, never()).refreshToken(any(), any());
  }

  @Test
  void refreshToken_invalidToken_returns_401() throws Exception {
    when(tokenService.refreshToken(eq("invalid-refresh-token"), any()))
        .thenThrow(new InvalidTokenException("Token de refresco invalido"));

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .cookie(new Cookie("refresh_token", "invalid-refresh-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void refreshToken_userDeleted_returns_401() throws Exception {
    when(tokenService.refreshToken(eq("refresh-from-deleted-user"), any()))
        .thenThrow(new UserNotFoundException("Usuario no encontrado"));

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .cookie(new Cookie("refresh_token", "refresh-from-deleted-user")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }
}
