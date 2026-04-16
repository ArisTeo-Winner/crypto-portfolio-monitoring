package com.mx.cryptomonitor.user.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.io.Decoders;

@Service
public class RefreshTokenHashService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final SecretKeySpec keySpec;

  public RefreshTokenHashService(
      @Value("${security.refresh-token-hash-secret-base64}") String refreshHashSecretBase64) {
    if (refreshHashSecretBase64 == null || refreshHashSecretBase64.isBlank()) {
      throw new IllegalStateException("Refresh token hash secret must be configured.");
    }

    byte[] keyBytes = Decoders.BASE64.decode(refreshHashSecretBase64);
    if (keyBytes.length < 32) {
      throw new IllegalStateException(
          "Refresh token hash secret must decode to at least 32 bytes.");
    }
    this.keySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
  }

  public String hmacSha256(String tokenValue) {
    if (tokenValue == null || tokenValue.isBlank()) {
      throw new IllegalArgumentException("Refresh token is required");
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(keySpec);
      byte[] digest = mac.doFinal(tokenValue.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to hash refresh token", ex);
    }
  }
}
