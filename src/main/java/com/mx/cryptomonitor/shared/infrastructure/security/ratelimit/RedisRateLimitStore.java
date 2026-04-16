package com.mx.cryptomonitor.shared.infrastructure.security.ratelimit;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RedisRateLimitStore implements RateLimitStore {

  private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>();
  private static final DefaultRedisScript<Long> FIXED_WINDOW_WITH_BLOCK_SCRIPT =
      new DefaultRedisScript<>();

  static {
    FIXED_WINDOW_SCRIPT.setResultType(Long.class);
    FIXED_WINDOW_SCRIPT.setScriptText(
        """
        local zsetKey = KEYS[1]
        local seqKey = KEYS[2]
        local now = tonumber(ARGV[1])
        local windowMs = tonumber(ARGV[2])
        local maxAttempts = tonumber(ARGV[3])
        redis.call('ZREMRANGEBYSCORE', zsetKey, 0, now - windowMs)
        local count = redis.call('ZCARD', zsetKey)
        if count >= maxAttempts then
          local oldest = redis.call('ZRANGE', zsetKey, 0, 0, 'WITHSCORES')
          local retryMs = 1
          if oldest ~= nil and oldest[2] ~= nil then
            retryMs = (tonumber(oldest[2]) + windowMs) - now
          end
          if retryMs < 1 then
            retryMs = 1
          end
          redis.call('PEXPIRE', zsetKey, windowMs)
          redis.call('PEXPIRE', seqKey, windowMs)
          return math.ceil(retryMs / 1000)
        end
        local sequence = redis.call('INCR', seqKey)
        local member = tostring(now) .. '-' .. tostring(sequence)
        redis.call('ZADD', zsetKey, now, member)
        redis.call('PEXPIRE', zsetKey, windowMs)
        redis.call('PEXPIRE', seqKey, windowMs)
        return 0
        """);

    FIXED_WINDOW_WITH_BLOCK_SCRIPT.setResultType(Long.class);
    FIXED_WINDOW_WITH_BLOCK_SCRIPT.setScriptText(
        """
        local zsetKey = KEYS[1]
        local seqKey = KEYS[2]
        local blockKey = KEYS[3]
        local now = tonumber(ARGV[1])
        local windowMs = tonumber(ARGV[2])
        local maxAttempts = tonumber(ARGV[3])
        local blockMs = tonumber(ARGV[4])
        local blockTtl = redis.call('PTTL', blockKey)
        if blockTtl > 0 then
          return math.ceil(blockTtl / 1000)
        end
        if redis.call('EXISTS', blockKey) == 1 then
          redis.call('DEL', blockKey)
        end
        redis.call('ZREMRANGEBYSCORE', zsetKey, 0, now - windowMs)
        local count = redis.call('ZCARD', zsetKey)
        if count >= maxAttempts then
          redis.call('SET', blockKey, '1', 'PX', blockMs)
          redis.call('DEL', zsetKey)
          redis.call('DEL', seqKey)
          return math.ceil(blockMs / 1000)
        end
        local sequence = redis.call('INCR', seqKey)
        local member = tostring(now) .. '-' .. tostring(sequence)
        redis.call('ZADD', zsetKey, now, member)
        redis.call('PEXPIRE', zsetKey, windowMs)
        redis.call('PEXPIRE', seqKey, windowMs)
        return 0
        """);
  }

  private final StringRedisTemplate redisTemplate;
  private final InMemoryRateLimitStore fallbackStore;
  private final MeterRegistry meterRegistry;

  @Autowired
  public RedisRateLimitStore(
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry,
      @Value("${security.rate-limit.redis-fallback-enabled:true}") boolean fallbackEnabled) {
    this(redisTemplate, fallbackEnabled, meterRegistry);
  }

  public static RedisRateLimitStore forTests(
      StringRedisTemplate redisTemplate, boolean fallbackEnabled) {
    return new RedisRateLimitStore(redisTemplate, fallbackEnabled, new SimpleMeterRegistry());
  }

  private RedisRateLimitStore(
      StringRedisTemplate redisTemplate, boolean fallbackEnabled, MeterRegistry meterRegistry) {
    this.redisTemplate = redisTemplate;
    this.fallbackStore =
        fallbackEnabled ? new InMemoryRateLimitStore(System::currentTimeMillis) : null;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public long consumeFixedWindow(
      String namespace, String clientKey, int maxAttempts, long windowSeconds) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Long result =
          execute(
              FIXED_WINDOW_SCRIPT,
              List.of(zsetKey(namespace, clientKey), sequenceKey(namespace, clientKey)),
              nowMillis(),
              windowSeconds * 1000L,
              maxAttempts);
      long retryAfterSeconds = retryAfterSeconds(result);
      recordDecision(namespace, "fixed_window", retryAfterSeconds > 0 ? "limited" : "allowed");
      sample.stop(
          meterRegistry.timer(
              "cryptomonitor.ratelimit.redis.latency",
              "namespace",
              namespace,
              "mode",
              "fixed_window",
              "outcome",
              retryAfterSeconds > 0 ? "limited" : "allowed"));
      return retryAfterSeconds;
    } catch (RuntimeException ex) {
      sample.stop(
          meterRegistry.timer(
              "cryptomonitor.ratelimit.redis.latency",
              "namespace",
              namespace,
              "mode",
              "fixed_window",
              "outcome",
              "fallback"));
      return fallback(namespace, clientKey, false, maxAttempts, windowSeconds, 0L, ex);
    }
  }

  @Override
  public long consumeFixedWindowWithBlock(
      String namespace, String clientKey, int maxAttempts, long windowSeconds, long blockSeconds) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Long result =
          execute(
              FIXED_WINDOW_WITH_BLOCK_SCRIPT,
              List.of(
                  zsetKey(namespace, clientKey),
                  sequenceKey(namespace, clientKey),
                  blockKey(namespace, clientKey)),
              nowMillis(),
              windowSeconds * 1000L,
              maxAttempts,
              blockSeconds * 1000L);
      long retryAfterSeconds = retryAfterSeconds(result);
      recordDecision(
          namespace, "fixed_window_with_block", retryAfterSeconds > 0 ? "blocked" : "allowed");
      sample.stop(
          meterRegistry.timer(
              "cryptomonitor.ratelimit.redis.latency",
              "namespace",
              namespace,
              "mode",
              "fixed_window_with_block",
              "outcome",
              retryAfterSeconds > 0 ? "blocked" : "allowed"));
      return retryAfterSeconds;
    } catch (RuntimeException ex) {
      sample.stop(
          meterRegistry.timer(
              "cryptomonitor.ratelimit.redis.latency",
              "namespace",
              namespace,
              "mode",
              "fixed_window_with_block",
              "outcome",
              "fallback"));
      return fallback(namespace, clientKey, true, maxAttempts, windowSeconds, blockSeconds, ex);
    }
  }

  private Long execute(DefaultRedisScript<Long> script, List<String> keys, Object... args) {
    String[] serializedArgs =
        java.util.Arrays.stream(args).map(String::valueOf).toArray(String[]::new);
    return redisTemplate.execute(script, keys, (Object[]) serializedArgs);
  }

  private long retryAfterSeconds(Long result) {
    if (result == null) {
      return 0L;
    }
    return Math.max(0L, result);
  }

  private long fallback(
      String namespace,
      String clientKey,
      boolean blockMode,
      int maxAttempts,
      long windowSeconds,
      long blockSeconds,
      RuntimeException ex) {
    if (fallbackStore == null) {
      throw ex;
    }
    log.warn(
        "Redis rate limiting unavailable for {}. Falling back to in-memory limiter.",
        namespace,
        ex);
    recordDecision(namespace, blockMode ? "fixed_window_with_block" : "fixed_window", "fallback");
    if (blockMode) {
      return fallbackStore.consumeFixedWindowWithBlock(
          namespace, clientKey, maxAttempts, windowSeconds, blockSeconds);
    }
    return fallbackStore.consumeFixedWindow(namespace, clientKey, maxAttempts, windowSeconds);
  }

  private long nowMillis() {
    return System.currentTimeMillis();
  }

  private String zsetKey(String namespace, String clientKey) {
    return "ratelimit:" + namespace + ":" + clientKey + ":events";
  }

  private String sequenceKey(String namespace, String clientKey) {
    return "ratelimit:" + namespace + ":" + clientKey + ":seq";
  }

  private String blockKey(String namespace, String clientKey) {
    return "ratelimit:" + namespace + ":" + clientKey + ":blocked";
  }

  private void recordDecision(String namespace, String mode, String outcome) {
    meterRegistry
        .counter(
            "cryptomonitor.ratelimit.requests",
            "namespace",
            namespace,
            "mode",
            mode,
            "outcome",
            outcome)
        .increment();
  }
}
