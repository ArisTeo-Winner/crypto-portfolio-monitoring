package com.mx.cryptomonitor.user.infrastructure.security;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.security.oauth.AuthenticatedUserPrincipal;

@Component
public class CurrentUserResolverAdapter implements CurrentUserPort {

  private final UserRepository userRepository;

  public CurrentUserResolverAdapter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UUID resolveUserId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthenticationCredentialsNotFoundException("User not authenticated");
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal) {
      return authenticatedUserPrincipal.getUserId();
    }

    if (principal instanceof UserDetails userDetails) {
      return userRepository
          .findByEmail(userDetails.getUsername())
          .map(user -> user.getId())
          .orElseThrow(
              () ->
                  new AuthenticationCredentialsNotFoundException(
                      "Authenticated user could not be resolved"));
    }

    if (principal instanceof String username && !"anonymousUser".equals(username)) {
      return userRepository
          .findByEmail(username)
          .map(user -> user.getId())
          .orElseThrow(
              () ->
                  new AuthenticationCredentialsNotFoundException(
                      "Authenticated user could not be resolved"));
    }

    throw new AuthenticationCredentialsNotFoundException(
        "Authenticated principal is not supported");
  }
}
