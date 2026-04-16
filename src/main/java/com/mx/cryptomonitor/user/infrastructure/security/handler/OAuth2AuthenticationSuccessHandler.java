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

import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
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

      JwtResponse jwt = authService.issueTokensForUser(user, request);
      response.sendRedirect(buildSuccessRedirect(jwt));

    } catch (Exception e) {
      log.error("Error en success handler", e);
      redirectToFailure(response, "OAUTH2_TOKEN_ISSUE_FAILED");
    }
  }

  private String buildSuccessRedirect(JwtResponse jwt) {
    return frontendBaseUrl
        + "/auth/callback#accessToken="
        + urlEncode(jwt.accessToken())
        + "&refreshToken="
        + urlEncode(jwt.refreshToken());
  }

  private void redirectToFailure(HttpServletResponse response, String errorCode)
      throws IOException {
    response.sendRedirect(frontendBaseUrl + "/login?oauth_error=" + urlEncode(errorCode));
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
