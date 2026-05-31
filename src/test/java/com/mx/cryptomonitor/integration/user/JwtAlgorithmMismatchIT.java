package com.mx.cryptomonitor.integration.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * Verifica que el JwtRequestFilter rechaza tokens con algoritmo incorrecto.
 *
 * <p>El caso concreto que motivó estos tests: un cliente enviaba un token HS256
 * (generado en Postman o jwt.io con un secret simétrico) contra un servidor que
 * espera RS256. El filtro lanzaba UnsupportedJwtException sin producir un 401
 * limpio, lo que dificultaba el diagnóstico.
 */
class JwtAlgorithmMismatchIT extends UserModuleIntegrationTest {

  @Test
  void request_with_HS256_token_must_return_401() throws Exception {
    String hs256Token =
        Jwts.builder()
            .setSubject("attacker@test.com")
            .signWith(Keys.hmacShaKeyFor(new byte[32]), SignatureAlgorithm.HS256)
            .compact();

    mockMvc
        .perform(get("/api/v1/me/sessions").header("Authorization", "Bearer " + hs256Token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void request_with_valid_RS256_token_must_return_200() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(
            get("/api/v1/me/sessions").header("Authorization", "Bearer " + tokens.accessToken()))
        .andExpect(status().isOk());
  }

  @Test
  void request_with_no_token_must_return_401() throws Exception {
    mockMvc.perform(get("/api/v1/me/sessions")).andExpect(status().isUnauthorized());
  }

  @Test
  void request_with_malformed_token_must_return_401() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/sessions").header("Authorization", "Bearer not.a.jwt"))
        .andExpect(status().isUnauthorized());
  }
}
