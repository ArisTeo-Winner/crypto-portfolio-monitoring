package com.mx.cryptomonitor.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.security.handler.OAuth2AuthenticationSuccessHandler;
import com.mx.cryptomonitor.user.infrastructure.security.oidc.OidcUserWithDomain;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerUnitTest {

  @Test
  void shouldRedirectToFrontendCallbackWithTokens() throws Exception {
    AuthService authService = mock(AuthService.class);
    UserRepository userRepository = mock(UserRepository.class);
    ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);

    OAuth2AuthenticationSuccessHandler handler =
        new OAuth2AuthenticationSuccessHandler(
            userRepository, authService, "http://localhost:3000");

    User user = User.builder().email("h@example.com").username("h").build();
    var principal = new OidcUserWithDomain(user, java.util.List.of(), null, null);

    var auth = new UsernamePasswordAuthenticationToken(principal, null);
    var request = mock(HttpServletRequest.class);
    var response = new MockHttpServletResponse();

    when(authService.issueTokensForUser(eq(user), any())).thenReturn(new AuthResult("A", "R"));

    handler.onAuthenticationSuccess(request, response, auth);

    assertThat(response.getStatus()).isEqualTo(302);
    // El refresh token ya NO viaja en la URL — va como cookie HttpOnly en el header Set-Cookie
    assertThat(response.getRedirectedUrl())
        .isEqualTo("http://localhost:3000/auth/callback#accessToken=A");
    assertThat(response.getHeader("Set-Cookie")).contains("refresh_token=R").contains("HttpOnly");

    verify(userRepository).save(savedUserCaptor.capture());
    assertThat(savedUserCaptor.getValue()).isSameAs(user);
    assertThat(savedUserCaptor.getValue().getLastLogin()).isNotNull();
    assertThat(savedUserCaptor.getValue().getLastLogin()).isBeforeOrEqualTo(LocalDateTime.now());
    verify(authService).issueTokensForUser(eq(user), eq(request));
  }

  @Test
  void shouldRedirectToLoginWithErrorWhenPrincipalIsUnexpected() throws Exception {
    AuthService authService = mock(AuthService.class);
    UserRepository userRepository = mock(UserRepository.class);

    OAuth2AuthenticationSuccessHandler handler =
        new OAuth2AuthenticationSuccessHandler(
            userRepository, authService, "http://localhost:3000");

    var auth = new UsernamePasswordAuthenticationToken("unexpected-principal", null);
    var request = mock(HttpServletRequest.class);
    var response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(request, response, auth);

    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getRedirectedUrl())
        .isEqualTo("http://localhost:3000/login?oauth_error=OAUTH2_PRINCIPAL_INVALID");

    verifyNoInteractions(userRepository, authService);
  }
}
