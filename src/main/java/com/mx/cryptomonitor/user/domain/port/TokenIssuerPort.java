package com.mx.cryptomonitor.user.domain.port;

import java.util.UUID;

public interface TokenIssuerPort {
  String generateAccessToken(String email, UUID sessionId);

  String generateRefreshToken(String email);

  long getRefreshExpiration();
}
