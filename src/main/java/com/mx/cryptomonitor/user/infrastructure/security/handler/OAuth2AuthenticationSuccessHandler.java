package com.mx.cryptomonitor.user.infrastructure.security.handler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.security.oidc.OidcUserWithDomain;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository userRepository;
  private final AuthService authService;
  private final String frontendBaseUrl;

  public OAuth2AuthenticationSuccessHandler(
      UserRepository userRepository,
      AuthService authService,
      @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
    this.userRepository = userRepository;
    this.authService = authService;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  @Override
  @Transactional
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    try {
      if (!(authentication.getPrincipal() instanceof OidcUserWithDomain principal)) {
        log.error("Principal inesperado : {}", authentication.getClass());
        redirectToFailure(response, "OAUTH2_PRINCIPAL_INVALID");
        return;
      }

      User user = principal.domainUser();
      user.setLastLogin(LocalDateTime.now());
      userRepository.save(user);

      AuthResult result = authService.issueTokensForUser(user, request);
      // El refresh token va en la cookie HttpOnly; el access token en el fragment de la URL
      // para que el frontend lo capture en memoria (no localStorage).
      response.setHeader(
          "Set-Cookie",
          org.springframework.http.ResponseCookie.from("refresh_token", result.rawRefreshToken())
              .httpOnly(true)
              .path("/api/v1/")
              .sameSite("Strict")
              .build()
              .toString());
      response.sendRedirect(buildSuccessRedirect(result));

    } catch (Exception e) {
      log.error("Error en success handler", e);
      redirectToFailure(response, "OAUTH2_TOKEN_ISSUE_FAILED");
    }
  }

  private String buildSuccessRedirect(AuthResult result) {
    // Solo el accessToken viaja en el fragment — el refreshToken ya fue escrito como cookie.
    return frontendBaseUrl + "/auth/callback#accessToken=" + urlEncode(result.accessToken());
  }

  private void redirectToFailure(HttpServletResponse response, String errorCode)
      throws IOException {
    response.sendRedirect(frontendBaseUrl + "/login?oauth_error=" + urlEncode(errorCode));
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
