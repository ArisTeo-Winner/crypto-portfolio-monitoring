package com.mx.cryptomonitor.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
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

    OAuth2AuthenticationSuccessHandler handler =
        new OAuth2AuthenticationSuccessHandler(
            userRepository, authService, "http://localhost:3000");

    User user = User.builder().email("h@example.com").username("h").build();
    var principal = new OidcUserWithDomain(user, java.util.List.of(), null, null);

    var auth = new UsernamePasswordAuthenticationToken(principal, null);
    var request = mock(HttpServletRequest.class);
    var response = new MockHttpServletResponse();

    when(authService.issueTokensForUser(eq(user), any())).thenReturn(new JwtResponse("A", "R"));

    handler.onAuthenticationSuccess(request, response, auth);

    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getRedirectedUrl())
        .isEqualTo("http://localhost:3000/auth/callback#accessToken=A&refreshToken=R");

    verify(userRepository).save(any());
  }
}
