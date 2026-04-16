package com.mx.cryptomonitor.user.infrastructure.security.handler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomOAuth2FailureHandler implements AuthenticationFailureHandler {

  private final String frontendBaseUrl;

  public CustomOAuth2FailureHandler(
      @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
    this.frontendBaseUrl = frontendBaseUrl;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    response.sendRedirect(
        frontendBaseUrl
            + "/login?oauth_error="
            + URLEncoder.encode("OIDC_LOGIN_FAILED", StandardCharsets.UTF_8));
  }
}
