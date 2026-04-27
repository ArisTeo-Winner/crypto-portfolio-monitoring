package com.mx.cryptomonitor.user.infrastructure.security.handler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
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
    String errorCode = resolveErrorCode(exception);
    log.warn(
        "OAuth2/OIDC login failed on path={} errorCode={} message={}",
        request.getRequestURI(),
        errorCode,
        exception.getMessage(),
        exception);
    response.sendRedirect(
        frontendBaseUrl
            + "/login?oauth_error="
            + URLEncoder.encode("OIDC_LOGIN_FAILED", StandardCharsets.UTF_8)
            + "&oauth_error_code="
            + URLEncoder.encode(errorCode, StandardCharsets.UTF_8));
  }

  private String resolveErrorCode(AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
      String oauthErrorCode = oauth2Exception.getError().getErrorCode();
      if (oauthErrorCode != null && !oauthErrorCode.isBlank()) {
        return oauthErrorCode;
      }
    }

    String message = exception.getMessage();
    if (message != null) {
      String normalizedMessage = message.toLowerCase(Locale.ROOT);
      for (String knownCode :
          List.of(
              "authorization_request_not_found",
              "invalid_client",
              "redirect_uri_mismatch",
              "access_denied",
              "invalid_request",
              "invalid_scope",
              "invalid_token_response",
              "invalid_user_info_response")) {
        if (normalizedMessage.contains(knownCode)) {
          return knownCode;
        }
      }
    }

    String simpleName = exception.getClass().getSimpleName();
    if (simpleName == null || simpleName.isBlank()) {
      return "authentication_failed";
    }
    return simpleName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }
}
