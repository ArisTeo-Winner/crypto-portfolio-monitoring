package com.mx.cryptomonitor.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;

class JwtTokenUtilUnitTest {

  private JwtTokenUtil jwtTokenUtil;
  private PrivateKey privateKey;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();
    privateKey = keyPair.getPrivate();

    String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

    jwtTokenUtil = new JwtTokenUtil();
    ReflectionTestUtils.setField(jwtTokenUtil, "privateKeyBase64", privateKeyBase64);
    ReflectionTestUtils.setField(jwtTokenUtil, "publicKeyBase64", publicKeyBase64);
    ReflectionTestUtils.setField(jwtTokenUtil, "accessTokenExpiration", 3_600_000L);
    ReflectionTestUtils.setField(jwtTokenUtil, "refreshTokenExpiration", 604_800_000L);
    jwtTokenUtil.init();
  }

  @Test
  void generateAccessToken_must_use_RS256_algorithm() {
    String token = jwtTokenUtil.generateAccessToken("user@test.com", UUID.randomUUID());

    String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
    assertThat(headerJson).contains("\"RS256\"");
  }

  @Test
  void generateAccessToken_must_contain_session_id_claim() {
    UUID sessionId = UUID.randomUUID();
    String token = jwtTokenUtil.generateAccessToken("user@test.com", sessionId);

    Claims claims = jwtTokenUtil.getClaimsFromToken(token);
    assertThat(claims.get("session_id", String.class)).isEqualTo(sessionId.toString());
    assertThat(claims.getSubject()).isEqualTo("user@test.com");
  }

  @Test
  void generateRefreshToken_must_use_RS256_algorithm() {
    String token = jwtTokenUtil.generateRefreshToken("user@test.com");

    String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
    assertThat(headerJson).contains("\"RS256\"");
  }

  // El token HS256 simula lo que ocurre cuando un cliente envía un token
  // generado con un secret simétrico (Postman, jwt.io, ambiente incorrecto).
  // El filtro debe rechazarlo — este test verifica que JwtTokenUtil lanza
  // UnsupportedJwtException antes de que el request llegue a ningún controlador.
  @Test
  void parseToken_must_reject_HS256_token_with_UnsupportedJwtException() {
    String hs256Token =
        Jwts.builder()
            .setSubject("attacker@test.com")
            .signWith(Keys.hmacShaKeyFor(new byte[32]), SignatureAlgorithm.HS256)
            .compact();

    assertThatThrownBy(() -> jwtTokenUtil.getClaimsFromToken(hs256Token))
        .isInstanceOf(UnsupportedJwtException.class)
        .hasMessageContaining("HS256");
  }

  @Test
  void parseToken_must_reject_token_signed_with_wrong_RSA_private_key() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    PrivateKey foreignPrivateKey = kpg.generateKeyPair().getPrivate();

    String foreignToken =
        Jwts.builder()
            .setSubject("foreign@test.com")
            .signWith(foreignPrivateKey, SignatureAlgorithm.RS256)
            .compact();

    assertThatThrownBy(() -> jwtTokenUtil.getClaimsFromToken(foreignToken))
        .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
  }

  @Test
  void init_must_fail_fast_when_private_key_is_not_RSA() {
    JwtTokenUtil broken = new JwtTokenUtil();
    ReflectionTestUtils.setField(broken, "privateKeyBase64", "bm90LXZhbGlkLWtleQ==");
    ReflectionTestUtils.setField(broken, "publicKeyBase64", "bm90LXZhbGlkLWtleQ==");
    ReflectionTestUtils.setField(broken, "accessTokenExpiration", 3_600_000L);
    ReflectionTestUtils.setField(broken, "refreshTokenExpiration", 604_800_000L);

    assertThatThrownBy(broken::init).isInstanceOf(IllegalStateException.class);
  }
}
