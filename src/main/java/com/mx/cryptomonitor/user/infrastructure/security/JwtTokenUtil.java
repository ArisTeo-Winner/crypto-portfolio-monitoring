package com.mx.cryptomonitor.user.infrastructure.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Component
public class JwtTokenUtil {

  private SecretKey secret;

  @Value("${jwt.secret-base64}")
  private String jwtSecretBase64;

  @Value("${jwt.access-token-expiration}")
  private long accessTokenExpiration;

  @Value("${jwt.refresh-token-expiration}")
  private long refreshTokenExpiration;

  @PostConstruct
  public void init() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtSecretBase64);
    if (keyBytes.length < 32) {
      throw new IllegalStateException(
          "jwt.secret-base64 debe decodificar a >= 32 bytes (256 bits)");
    }
    this.secret = Keys.hmacShaKeyFor(keyBytes);
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    // TODO Auto-generated method stub
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  private Claims extractAllClaims(String token) {
    // TODO Auto-generated method stub
    return Jwts.parserBuilder().setSigningKey(secret).build().parseClaimsJws(token).getBody();
  }

  public Boolean isTokenExpired(String token) {
    Date exp = extractClaim(token, Claims::getExpiration);
    return exp.before(new Date());
  }

  public boolean validateToken(String token, UserDetails userDetails) {
    // TODO Auto-generated method stub
    final String username = extractUsername(token);
    return username.equalsIgnoreCase(userDetails.getUsername()) && !isTokenExpired(token);
  }

  public String generateAccessToken(String email, UUID sessionId) {

    Map<String, Object> claims = new HashMap<>();
    claims.put("session_id", sessionId.toString());

    return Jwts.builder()
        .setClaims(claims)
        .setSubject(email)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
        .signWith(secret, SignatureAlgorithm.HS256)
        .compact();
  }

  public String generateRefreshToken(String email) {
    return Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setSubject(email)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
        .signWith(secret, SignatureAlgorithm.HS256)
        .compact();
  }

  public String getUsernameFromToken(String token) {
    return extractUsername(token);
  }

  // Obtener los claims del token
  public Claims getClaimsFromToken(String token) {
    return extractAllClaims(token);
  }

  // Obtener el email (subject) del token
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
