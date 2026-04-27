package com.mx.cryptomonitor.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.mx.cryptomonitor.user.infrastructure.security.handler.CustomOAuth2FailureHandler;

class CustomOAuth2FailureHandlerUnitTest {

  @Test
  void shouldRedirectToFrontendLoginWithOauthError() throws Exception {
    CustomOAuth2FailureHandler handler = new CustomOAuth2FailureHandler("http://localhost:3000");

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException exception = new AuthenticationException("cancelled by user") {};

    handler.onAuthenticationFailure(request, response, exception);

    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getRedirectedUrl())
        .isEqualTo(
            "http://localhost:3000/login?oauth_error=OIDC_LOGIN_FAILED&oauth_error_code=authentication_failed");
  }

  @Test
  void shouldExposeSpecificOauthErrorCodeWhenPresent() throws Exception {
    CustomOAuth2FailureHandler handler = new CustomOAuth2FailureHandler("http://localhost:3000");

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
    MockHttpServletResponse response = new MockHttpServletResponse();
    OAuth2AuthenticationException exception =
        new OAuth2AuthenticationException(new OAuth2Error("invalid_client"), "Bad client secret");

    handler.onAuthenticationFailure(request, response, exception);

    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getRedirectedUrl())
        .isEqualTo(
            "http://localhost:3000/login?oauth_error=OIDC_LOGIN_FAILED&oauth_error_code=invalid_client");
  }
}
