package com.mx.cryptomonitor.marketdata.infrastructure.outbound.redis;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketStateCachePort;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState.Status;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estado remoto de cada mercado en Redis (ADR-0033): {@code marketdata:market-state:{MARKET}}. La
 * campana se guarda como instante absoluto, de modo que todos los símbolos de un mismo cierre
 * expiran a la vez sin volver a consultar al proveedor. Fallos de Redis equivalen a "no hay dato".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketStateRedisAdapter implements MarketStateCachePort {

  static final String KEY_PREFIX = "marketdata:market-state:";

  private static final String STATUS = "status";
  private static final String OPENS_AT = "opensAt";
  private static final String EXPIRES_AT = "expiresAt";
  private static final String CHECKED_AT = "checkedAt";

  private final StringRedisTemplate redisTemplate;
  private final Clock clock;

  @Override
  public Optional<RemoteMarketState> find(Market market) {
    try {
      Map<Object, Object> fields = redisTemplate.opsForHash().entries(KEY_PREFIX + market.name());
      // Si el EXPIREAT no llego a aplicarse la llave no caduca sola: la vigencia guardada manda.
      if (fields.isEmpty() || !Instant.parse((String) fields.get(EXPIRES_AT)).isAfter(now())) {
        return Optional.empty();
      }
      String opensAt = (String) fields.get(OPENS_AT);
      return Optional.of(
          new RemoteMarketState(
              Status.valueOf((String) fields.get(STATUS)),
              opensAt == null || opensAt.isEmpty() ? null : Instant.parse(opensAt)));
    } catch (RuntimeException ex) {
      log.warn("No se pudo leer el estado remoto de {}: {}", market, ex.toString());
      return Optional.empty();
    }
  }

  @Override
  public void save(Market market, RemoteMarketState state, Instant expiresAt) {
    try {
      String key = KEY_PREFIX + market.name();
      Map<String, String> fields = new HashMap<>();
      fields.put(STATUS, state.status().name());
      fields.put(OPENS_AT, state.opensAt() == null ? "" : state.opensAt().toString());
      fields.put(EXPIRES_AT, expiresAt.toString());
      fields.put(CHECKED_AT, now().toString());
      redisTemplate.opsForHash().putAll(key, fields);
      redisTemplate.expireAt(key, expiresAt);
    } catch (RuntimeException ex) {
      log.warn("No se pudo guardar el estado remoto de {}: {}", market, ex.toString());
    }
  }

  private Instant now() {
    return clock.instant();
  }
}
