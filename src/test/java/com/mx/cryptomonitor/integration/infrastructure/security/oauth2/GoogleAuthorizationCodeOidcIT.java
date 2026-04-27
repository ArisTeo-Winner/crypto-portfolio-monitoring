package com.mx.cryptomonitor.integration.infrastructure.security.oauth2;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Integration test: OAuth2 Authorization Code + OIDC (Google) using WireMock (no real Google
 * calls).
 *
 * <p>Validates: - /oauth2/authorization/google produces an auth redirect with state -
 * /login/oauth2/code/google exchanges the code, validates ID token signature, persists user/link,
 * and emits internal JWT tokens (access + refresh) in JSON body. - issued access token can call
 * /api/v1/users/me (API-first verification of persistence).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class GoogleAuthorizationCodeOidcIT {

  private static final Logger logger = LoggerFactory.getLogger(GoogleAuthorizationCodeOidcIT.class);

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  static final WireMockServer google = new WireMockServer(wireMockConfig().dynamicPort());
  static final RSAKey rsaJwk;

  static {
    google.start();
    try {
      rsaJwk = new RSAKeyGenerator(2048).keyID("test-kid").generate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    r.add(
        "spring.jpa.properties.hibernate.dialect",
        () -> "org.hibernate.dialect.PostgreSQLDialect");
    r.add("spring.flyway.enabled", () -> "false");

    logger.info("datasource.url: {}", postgres.getJdbcUrl());

    // JWT settings for internal token issuance in tests
    String secret =
        Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
    r.add("jwt.secret-base64", () -> secret);
    r.add("jwt.access-token-expiration", () -> "3600000"); // ms
    r.add("jwt.refresh-token-expiration", () -> "86400000"); // ms

    // Override Google endpoints to WireMock
    r.add(
        "spring.security.oauth2.client.provider.google.authorization-uri",
        () -> google.baseUrl() + "/o/oauth2/v2/auth");
    r.add(
        "spring.security.oauth2.client.provider.google.token-uri",
        () -> google.baseUrl() + "/token");
    r.add(
        "spring.security.oauth2.client.provider.google.jwk-set-uri",
        () -> google.baseUrl() + "/jwks");
    r.add(
        "spring.security.oauth2.client.provider.google.user-info-uri",
        () -> google.baseUrl() + "/userinfo");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void resetWireMock() {
    google.resetAll();
    System.out.printf(
        "TC host=%s port=%s jdbc=%s%n",
        postgres.getHost(), postgres.getMappedPort(5432), postgres.getJdbcUrl());
  }

  @AfterAll
  static void stopWireMock() {
    google.stop();
  }

  @Test
  void authorizationCodeOidc_success_emitsInternalTokens_andAccessTokenWorksOnMeEndpoint()
      throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String sub = "sub-" + suffix;
    String email = "oauth_" + suffix + "@example.com";

    // JWKS for ID token validation (signature)
    String jwksJson =
        objectMapper.writeValueAsString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
    google.stubFor(
        get(urlEqualTo("/jwks"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(jwksJson)));

    // 1) Start flow: creates state and redirects to provider authorization endpoint
    MvcResult start =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    String location = start.getResponse().getHeader("Location");
    assertThat(location).isNotBlank();
    assertThat(location).startsWith(google.baseUrl() + "/o/oauth2/v2/auth");

    String state =
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
    assertThat(state).isNotBlank();
    state = URLDecoder.decode(state, StandardCharsets.UTF_8);

    String nonce =
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("nonce");
    assertThat(nonce)
        .as("OIDC nonce should be present in authorization request and must be echoed in ID token")
        .isNotBlank();
    nonce = URLDecoder.decode(nonce, StandardCharsets.UTF_8);

    MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
    assertThat(session)
        .as("OAuth2 state must be stored server-side for callback validation")
        .isNotNull();

    String idToken = signedIdToken(sub, email, "test-id", nonce);

    // Token endpoint: Spring Security exchanges code -> tokens
    String tokenResponse =
        objectMapper.writeValueAsString(
            Map.of(
                "access_token", "google-at",
                "token_type", "Bearer",
                "expires_in", 3600,
                "scope", "openid email profile",
                "id_token", idToken));

    google.stubFor(
        post(urlEqualTo("/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(tokenResponse)));

    // UserInfo endpoint (OIDC)
    String userInfo =
        objectMapper.writeValueAsString(
            Map.of(
                "sub",
                sub,
                "email",
                email,
                "email_verified",
                true,
                "given_name",
                "Leo",
                "family_name",
                "Manzano"));

    google.stubFor(
        get(urlEqualTo("/userinfo"))
            .withHeader("Authorization", equalTo("Bearer google-at"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(userInfo)));

    // 2) Callback: exchange code + validate ID token + issue internal JWT tokens
    MvcResult callback =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/login/oauth2/code/google")
                    .session(session)
                    .param("code", "test-code")
                    .param("state", state))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andReturn();

    Map<String, String> json =
        objectMapper.readValue(
            callback.getResponse().getContentAsString(),
            new TypeReference<Map<String, String>>() {});
    String accessToken = json.get("accessToken");
    assertThat(accessToken).isNotBlank();

    // 3) API-first verification: internal JWT can call /api/v1/users/me
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/users/me")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.email").value(email));

    // Broken access control check: OAuth2-created user must not have ADMIN by default
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void callback_withWrongState_mustNotIssueTokens() throws Exception {
    // minimal stubs to let the callback attempt proceed
    String jwksJson =
        objectMapper.writeValueAsString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
    google.stubFor(
        get(urlEqualTo("/jwks"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(jwksJson)));
    google.stubFor(
        post(urlEqualTo("/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "access_token", "google-at",
                                "token_type", "Bearer",
                                "expires_in", 3600,
                                "scope", "openid email profile",
                                "id_token",
                                    signedIdToken(
                                        "sub-x", "x@example.com", "test-id", "nonce"))))));
    google.stubFor(
        get(urlEqualTo("/userinfo"))
            .withHeader("Authorization", equalTo("Bearer google-at"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"sub\":\"sub-x\",\"email\":\"x@example.com\"}")));

    MvcResult start =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
    assertThat(session).isNotNull();

    // wrong state -> should fail before invoking success handler
    mockMvc
        .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/login/oauth2/code/google")
                .session(session)
                .param("code", "test-code")
                .param("state", "tampered-state"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void callback_withoutSession_mustNotExchangeCode() throws Exception {
    // Start flow only to obtain a valid state/nonce; then call callback without session -> should
    // fail early.
    MvcResult start =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    String location = start.getResponse().getHeader("Location");
    assertThat(location).isNotBlank();
    String state =
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
    assertThat(state).isNotBlank();
    state = URLDecoder.decode(state, StandardCharsets.UTF_8);

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/login/oauth2/code/google")
                .param("code", "test-code")
                .param("state", state))
        .andExpect(status().is3xxRedirection());

    // no session => authorization_request_not_found => must not hit /token
    google.verify(0, postRequestedFor(urlEqualTo("/token")));
  }

  @Test
  void callback_withAudienceMismatch_mustNotIssueTokens() throws Exception {
    OAuthStart start = startFlow();
    stubJwks();
    stubTokenAndUserInfo(
        signedIdTokenCustom(
            "sub-aud-mismatch",
            "aud_mismatch@example.com",
            "wrong-audience",
            start.nonce,
            "https://accounts.google.com",
            Instant.now().plusSeconds(300),
            rsaJwk,
            rsaJwk.getKeyID()));

    mockMvc
        .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/login/oauth2/code/google")
                .session(start.session)
                .param("code", "test-code")
                .param("state", start.state))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void callback_withIssuerMismatch_mustNotIssueTokens() throws Exception {
    OAuthStart start = startFlow();
    stubJwks();
    stubTokenAndUserInfo(
        signedIdTokenCustom(
            "sub-iss-mismatch",
            "iss_mismatch@example.com",
            "test-id",
            start.nonce,
            "https://issuer.evil.example",
            Instant.now().plusSeconds(300),
            rsaJwk,
            rsaJwk.getKeyID()));

    mockMvc
        .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/login/oauth2/code/google")
                .session(start.session)
                .param("code", "test-code")
                .param("state", start.state))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void callback_withExpiredIdToken_mustNotIssueTokens() throws Exception {
    OAuthStart start = startFlow();
    stubJwks();
    stubTokenAndUserInfo(
        signedIdTokenCustom(
            "sub-expired",
            "expired@example.com",
            "test-id",
            start.nonce,
            "https://accounts.google.com",
            Instant.now().minusSeconds(5),
            rsaJwk,
            rsaJwk.getKeyID()));

    mockMvc
        .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/login/oauth2/code/google")
                .session(start.session)
                .param("code", "test-code")
                .param("state", start.state))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void callback_withInvalidSignature_mustNotIssueTokens() throws Exception {
    OAuthStart start = startFlow();
    stubJwks();

    RSAKey other = new RSAKeyGenerator(2048).keyID("other-kid").generate();
    // signed with other key, but kid points to expected key -> signature validation must fail
    stubTokenAndUserInfo(
        signedIdTokenCustom(
            "sub-bad-sig",
            "bad_sig@example.com",
            "test-id",
            start.nonce,
            "https://accounts.google.com",
            Instant.now().plusSeconds(300),
            other,
            rsaJwk.getKeyID()));

    mockMvc
        .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/login/oauth2/code/google")
                .session(start.session)
                .param("code", "test-code")
                .param("state", start.state))
        .andExpect(status().is3xxRedirection());
  }

  private void stubJwks() throws Exception {
    String jwksJson =
        objectMapper.writeValueAsString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
    google.stubFor(
        get(urlEqualTo("/jwks"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(jwksJson)));
  }

  private void stubTokenAndUserInfo(String idToken) throws Exception {
    google.stubFor(
        post(urlEqualTo("/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "access_token", "google-at",
                                "token_type", "Bearer",
                                "expires_in", 3600,
                                "scope", "openid email profile",
                                "id_token", idToken)))));

    google.stubFor(
        get(urlEqualTo("/userinfo"))
            .withHeader("Authorization", equalTo("Bearer google-at"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"sub\":\"sub-x\",\"email\":\"x@example.com\"}")));
  }

  private OAuthStart startFlow() throws Exception {
    MvcResult start =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    String location = start.getResponse().getHeader("Location");
    assertThat(location).isNotBlank();

    String state =
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
    assertThat(state).isNotBlank();
    state = URLDecoder.decode(state, StandardCharsets.UTF_8);

    String nonce =
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("nonce");
    assertThat(nonce).isNotBlank();
    nonce = URLDecoder.decode(nonce, StandardCharsets.UTF_8);

    MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
    assertThat(session).isNotNull();

    return new OAuthStart(session, state, nonce);
  }

  private record OAuthStart(MockHttpSession session, String state, String nonce) {}

  private static String signedIdToken(String sub, String email, String aud, String nonce)
      throws Exception {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com")
            .audience(aud)
            .subject(sub)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("email", email)
            .claim("email_verified", true)
            .claim("given_name", "Leo")
            .claim("family_name", "Manzano")
            .claim("nonce", nonce)
            .build();

    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(JOSEObjectType.JWT)
            .keyID(rsaJwk.getKeyID())
            .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new RSASSASigner(rsaJwk.toPrivateKey()));
    return jwt.serialize();
  }

  private static String signedIdTokenCustom(
      String sub,
      String email,
      String aud,
      String nonce,
      String issuer,
      Instant exp,
      RSAKey signingKey,
      String headerKid)
      throws Exception {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(aud)
            .subject(sub)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(exp))
            .claim("email", email)
            .claim("email_verified", true)
            .claim("given_name", "Leo")
            .claim("family_name", "Manzano")
            .claim("nonce", nonce)
            .build();

    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(headerKid).build();

    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
    return jwt.serialize();
  }
}
