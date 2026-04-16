package com.mx.cryptomonitor.user.application.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenStoreService {

  private static final String TOKEN_KEY_PREFIX = "auth:rt:";
  private static final String USER_INDEX_PREFIX = "auth:rt:user:";

  private final StringRedisTemplate redisTemplate;
  private final RefreshTokenHashService refreshTokenHashService;

  public StoredRefreshToken store(
      String rawRefreshToken,
      UUID userId,
      UUID sessionId,
      LocalDateTime expiresAt,
      String ipAddress,
      String userAgent) {
    String tokenHash = refreshTokenHashService.hmacSha256(rawRefreshToken);
    String tokenKey = tokenKey(tokenHash);
    UUID tokenId = UUID.randomUUID();

    Map<String, String> payload = new HashMap<>();
    payload.put("tokenId", tokenId.toString());
    payload.put("userId", userId.toString());
    payload.put("sessionId", sessionId == null ? "" : sessionId.toString());
    payload.put("revoked", "false");
    payload.put("ipAddress", ipAddress == null ? "" : ipAddress);
    payload.put("userAgent", userAgent == null ? "" : userAgent);
    redisTemplate.opsForHash().putAll(tokenKey, payload);

    Duration ttl = computeTtl(expiresAt);
    redisTemplate.expire(tokenKey, ttl);

    String userIndexKey = userIndexKey(userId);
    redisTemplate.opsForSet().add(userIndexKey, tokenHash);
    redisTemplate.expire(userIndexKey, ttl.plusHours(1));

    return new StoredRefreshToken(tokenId, userId, sessionId, false);
  }

  public Optional<StoredRefreshToken> findByRawToken(String rawRefreshToken) {
    String tokenHash = refreshTokenHashService.hmacSha256(rawRefreshToken);
    String tokenKey = tokenKey(tokenHash);
    if (!Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey))) {
      return Optional.empty();
    }

    Object tokenIdValue = redisTemplate.opsForHash().get(tokenKey, "tokenId");
    Object userIdValue = redisTemplate.opsForHash().get(tokenKey, "userId");
    Object sessionIdValue = redisTemplate.opsForHash().get(tokenKey, "sessionId");
    Object revokedValue = redisTemplate.opsForHash().get(tokenKey, "revoked");

    if (tokenIdValue == null || userIdValue == null) {
      return Optional.empty();
    }

    UUID tokenId = UUID.fromString(tokenIdValue.toString());
    UUID userId = UUID.fromString(userIdValue.toString());
    UUID sessionId = null;
    if (sessionIdValue != null && !sessionIdValue.toString().isBlank()) {
      sessionId = UUID.fromString(sessionIdValue.toString());
    }
    boolean revoked = Boolean.parseBoolean(String.valueOf(revokedValue));
    return Optional.of(new StoredRefreshToken(tokenId, userId, sessionId, revoked));
  }

  public void markRevokedByRawToken(String rawRefreshToken) {
    String tokenHash = refreshTokenHashService.hmacSha256(rawRefreshToken);
    String tokenKey = tokenKey(tokenHash);
    if (Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey))) {
      redisTemplate.opsForHash().put(tokenKey, "revoked", "true");
    }
  }

  public void revokeAllByUserId(UUID userId) {
    String userIndexKey = userIndexKey(userId);
    Set<String> tokenHashes = redisTemplate.opsForSet().members(userIndexKey);
    if (tokenHashes != null) {
      tokenHashes.forEach(tokenHash -> redisTemplate.delete(tokenKey(tokenHash)));
    }
    redisTemplate.delete(userIndexKey);
  }

  private Duration computeTtl(LocalDateTime expiresAt) {
    long ttlSeconds =
        expiresAt.toEpochSecond(ZoneOffset.UTC) - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    if (ttlSeconds <= 0) {
      return Duration.ofSeconds(1);
    }
    return Duration.ofSeconds(ttlSeconds);
  }

  private String tokenKey(String tokenHash) {
    return TOKEN_KEY_PREFIX + tokenHash;
  }

  private String userIndexKey(UUID userId) {
    return USER_INDEX_PREFIX + userId;
  }

  public record StoredRefreshToken(UUID tokenId, UUID userId, UUID sessionId, boolean revoked) {}
}
