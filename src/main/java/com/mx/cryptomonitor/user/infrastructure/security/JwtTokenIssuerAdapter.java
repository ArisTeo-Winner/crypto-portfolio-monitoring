package com.mx.cryptomonitor.user.infrastructure.security;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.user.domain.port.TokenIssuerPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenIssuerAdapter implements TokenIssuerPort {

  private final JwtTokenUtil jwtTokenUtil;

  @Override
  public String generateAccessToken(String email, UUID sessionId) {
    return jwtTokenUtil.generateAccessToken(email, sessionId);
  }

  @Override
  public String generateRefreshToken(String email) {
    return jwtTokenUtil.generateRefreshToken(email);
  }

  @Override
  public long getRefreshExpiration() {
    return jwtTokenUtil.getRefreshExpiration();
  }
}
