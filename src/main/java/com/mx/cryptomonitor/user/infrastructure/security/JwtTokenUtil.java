package com.mx.cryptomonitor.user.infrastructure.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;

/**
 * Emisor y validador de JWT firmados con RS256 (RSA 2048).
 *
 * <p>Clave privada: PKCS8 DER codificado en Base64 → {@code JWT_PRIVATE_KEY_BASE64}. Clave pública:
 * X.509 DER codificado en Base64 → {@code JWT_PUBLIC_KEY_BASE64}.
 *
 * <p>La clave privada solo firma; la pública solo verifica. Un leak de la clave pública (que puede
 * publicarse libremente) no permite forjar tokens — a diferencia de HS256.
 */
@Component
public class JwtTokenUtil {

  @Value("${jwt.private-key-base64}")
  private String privateKeyBase64;

  @Value("${jwt.public-key-base64}")
  private String publicKeyBase64;

  @Value("${jwt.access-token-expiration}")
  private long accessTokenExpiration;

  @Value("${jwt.refresh-token-expiration}")
  private long refreshTokenExpiration;

  private PrivateKey privateKey;
  private PublicKey publicKey;

  @PostConstruct
  public void init() {
    try {
      KeyFactory kf = KeyFactory.getInstance("RSA");

      byte[] privBytes = Decoders.BASE64.decode(privateKeyBase64);
      this.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

      byte[] pubBytes = Decoders.BASE64.decode(publicKeyBase64);
      this.publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

    } catch (Exception e) {
      throw new IllegalStateException(
          "No se pudo inicializar JwtTokenUtil: verifica JWT_PRIVATE_KEY_BASE64 y"
              + " JWT_PUBLIC_KEY_BASE64",
          e);
    }
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    return claimsResolver.apply(extractAllClaims(token));
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(token).getBody();
  }

  public Boolean isTokenExpired(String token) {
    return extractClaim(token, Claims::getExpiration).before(new Date());
  }

  public boolean validateToken(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return username.equalsIgnoreCase(userDetails.getUsername()) && !isTokenExpired(token);
  }

  /**
   * Genera un access token RS256.
   *
   * <p>Claims incluidos: {@code sub} (email), {@code session_id}, {@code jti} (UUID único), {@code
   * iat}, {@code exp}.
   */
  public String generateAccessToken(String email, UUID sessionId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("session_id", sessionId.toString());

    return Jwts.builder()
        .setClaims(claims)
        .setId(UUID.randomUUID().toString()) // jti — identificador único por token
        .setSubject(email)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
        .signWith(
            privateKey, SignatureAlgorithm.RS256) // asimétrico: privada firma, pública verifica
        .compact();
  }

  /**
   * Genera un refresh token RS256.
   *
   * <p>El refresh token se almacena hasheado en Redis; este JWT es la representación en wire
   * format. El {@code jti} permite identificar el token individualmente en logs de auditoría.
   */
  public String generateRefreshToken(String email) {
    return Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setSubject(email)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
        .signWith(privateKey, SignatureAlgorithm.RS256)
        .compact();
  }

  public String getUsernameFromToken(String token) {
    return extractUsername(token);
  }

  public Claims getClaimsFromToken(String token) {
    return extractAllClaims(token);
  }

  public String getEmailFromToken(String token) {
    return getClaimsFromToken(token).getSubject();
  }

  public long getAccessExpiration() {
    return accessTokenExpiration;
  }

  public long getRefreshExpiration() {
    return refreshTokenExpiration;
  }
}
