package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Firma el JWT ES256 efímero que exige Coinbase Advanced Trade (CDP) en cada petición REST
 * (ADR-0023). El token dura 120 s y queda ligado a método + host + ruta vía el claim {@code uri},
 * así que se firma por petición (no se almacena ni se cachea).
 *
 * <p>La llave ({@code key-name} = id, {@code private-key} = PEM) se lee por configuración ({@code
 * external.providers.coinbase.cdp.*}), nunca hardcodeada ni escrita en logs. El PEM llega en
 * formato SEC1 ({@code -----BEGIN EC PRIVATE KEY-----}); se parsea una sola vez al construir. Si la
 * credencial no está configurada, {@link #isConfigured()} devuelve {@code false} y el adapter de
 * Coinbase se salta (la cadena cae al siguiente proveedor).
 */
@Component
public class CoinbaseJwtSigner {

  private static final String HOST = "api.coinbase.com";
  private static final String ISSUER = "cdp";
  private static final long TOKEN_TTL_SECONDS = 120L;

  private final String keyName;
  private final PrivateKey privateKey;
  private final SecureRandom secureRandom = new SecureRandom();
  private final Clock clock;

  @Autowired
  public CoinbaseJwtSigner(
      @Value("${external.providers.coinbase.cdp.key-name:}") String keyName,
      @Value("${external.providers.coinbase.cdp.private-key:}") String privateKeyPem,
      Clock clock) {
    this.keyName = keyName == null ? "" : keyName.trim();
    this.privateKey = parsePrivateKey(privateKeyPem);
    this.clock = clock;
  }

  public CoinbaseJwtSigner(String keyName, PrivateKey privateKey, Clock clock) {
    this.keyName = keyName;
    this.privateKey = privateKey;
    this.clock = clock;
  }

  public boolean isConfigured() {
    return privateKey != null && StringUtils.hasText(keyName);
  }

  public String sign(String method, String requestPath) {
    Instant now = Instant.now(clock);
    String uri = method + " " + HOST + requestPath;
    return Jwts.builder()
        .setHeaderParam("kid", keyName)
        .setHeaderParam("nonce", nonce())
        .setHeaderParam("typ", "JWT")
        .setIssuer(ISSUER)
        .setSubject(keyName)
        .setNotBefore(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(TOKEN_TTL_SECONDS)))
        .claim("uri", uri)
        .signWith(privateKey, SignatureAlgorithm.ES256)
        .compact();
  }

  private String nonce() {
    byte[] bytes = new byte[16];
    secureRandom.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  // El PEM puede venir con saltos de línea reales o escapados ("\n") según cómo lo inyecte el
  // entorno; se normaliza antes de parsear. Mensajes de error genéricos: no se expone material de
  // la llave.
  private static PrivateKey parsePrivateKey(String pem) {
    if (!StringUtils.hasText(pem)) {
      return null;
    }
    String normalized = pem.replace("\\n", "\n");
    try (PEMParser parser = new PEMParser(new StringReader(normalized))) {
      Object parsed = parser.readObject();
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      if (parsed instanceof PEMKeyPair pemKeyPair) {
        return converter.getKeyPair(pemKeyPair).getPrivate();
      }
      if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
        return converter.getPrivateKey(privateKeyInfo);
      }
      throw new IllegalStateException("Formato de llave privada CDP de Coinbase no soportado");
    } catch (IOException ex) {
      throw new IllegalStateException("No se pudo parsear la llave privada CDP de Coinbase", ex);
    }
  }
}
