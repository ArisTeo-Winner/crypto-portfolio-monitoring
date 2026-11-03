package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinbaseJwtSigner;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

class CoinbaseJwtSignerTest {

  private static final String KEY_NAME = "organizations/org-x/apiKeys/key-y";
  private static final String PATH = "/api/v3/brokerage/products/BTC-USD/candles";

  @Test
  void signsParseablePathBoundEs256Token() throws Exception {
    KeyPair keyPair = generateEcKeyPair();
    CoinbaseJwtSigner signer =
        new CoinbaseJwtSigner(KEY_NAME, toPkcs8Pem(keyPair), Clock.systemUTC());

    assertThat(signer.isConfigured()).isTrue();

    String token = signer.sign("GET", PATH);

    Jws<Claims> jws =
        Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(token);
    assertThat(jws.getBody().getIssuer()).isEqualTo("cdp");
    assertThat(jws.getBody().getSubject()).isEqualTo(KEY_NAME);
    assertThat(jws.getBody().get("uri"))
        .isEqualTo("GET api.coinbase.com/api/v3/brokerage/products/BTC-USD/candles");
    assertThat(jws.getBody().getExpiration()).isAfter(jws.getBody().getNotBefore());
    assertThat(jws.getHeader().get("kid")).isEqualTo(KEY_NAME);
    assertThat(jws.getHeader().get("nonce")).isNotNull();
  }

  @Test
  void uriClaimBindsToMethodAndPath() throws Exception {
    KeyPair keyPair = generateEcKeyPair();
    CoinbaseJwtSigner signer =
        new CoinbaseJwtSigner(KEY_NAME, toPkcs8Pem(keyPair), Clock.systemUTC());

    String token = signer.sign("GET", "/api/v3/brokerage/products/ETH-USD/candles");

    Jws<Claims> jws =
        Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(token);
    assertThat(jws.getBody().get("uri"))
        .isEqualTo("GET api.coinbase.com/api/v3/brokerage/products/ETH-USD/candles");
  }

  @Test
  void blankCredentialIsNotConfigured() {
    assertThat(new CoinbaseJwtSigner("", "", Clock.systemUTC()).isConfigured()).isFalse();
    assertThat(new CoinbaseJwtSigner(KEY_NAME, "", Clock.systemUTC()).isConfigured()).isFalse();
  }

  private static KeyPair generateEcKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  private static String toPkcs8Pem(KeyPair keyPair) {
    String base64 =
        Base64.getMimeEncoder(64, new byte[] {'\n'})
            .encodeToString(keyPair.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
  }
}
