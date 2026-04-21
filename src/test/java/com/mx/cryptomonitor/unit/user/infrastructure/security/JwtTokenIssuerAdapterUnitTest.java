package com.mx.cryptomonitor.unit.user.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenIssuerAdapter;
import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenUtil;

@ExtendWith(MockitoExtension.class)
class JwtTokenIssuerAdapterUnitTest {

  @Mock private JwtTokenUtil jwtTokenUtil;

  @InjectMocks private JwtTokenIssuerAdapter adapter;

  @Test
  void generateAccessToken_delegates_to_jwtTokenUtil() {
    UUID sessionId = UUID.randomUUID();
    when(jwtTokenUtil.generateAccessToken("user@example.com", sessionId))
        .thenReturn("access-token");

    String token = adapter.generateAccessToken("user@example.com", sessionId);

    assertThat(token).isEqualTo("access-token");
    verify(jwtTokenUtil).generateAccessToken("user@example.com", sessionId);
  }

  @Test
  void generateRefreshToken_delegates_to_jwtTokenUtil() {
    when(jwtTokenUtil.generateRefreshToken("user@example.com")).thenReturn("refresh-token");

    String token = adapter.generateRefreshToken("user@example.com");

    assertThat(token).isEqualTo("refresh-token");
    verify(jwtTokenUtil).generateRefreshToken("user@example.com");
  }

  @Test
  void getRefreshExpiration_delegates_to_jwtTokenUtil() {
    when(jwtTokenUtil.getRefreshExpiration()).thenReturn(604800000L);

    long expiration = adapter.getRefreshExpiration();

    assertThat(expiration).isEqualTo(604800000L);
    verify(jwtTokenUtil).getRefreshExpiration();
  }
}
