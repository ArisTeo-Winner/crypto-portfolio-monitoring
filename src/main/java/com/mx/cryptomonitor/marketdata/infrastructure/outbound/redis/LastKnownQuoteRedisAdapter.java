package com.mx.cryptomonitor.marketdata.infrastructure.outbound.redis;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.LastKnownQuotePort;
import com.mx.cryptomonitor.marketdata.domain.model.HeldQuoteSource;
import com.mx.cryptomonitor.marketdata.domain.model.StockQuote;

import lombok.extern.slf4j.Slf4j;

/**
 * Cotizaciones de acciones en Redis (ADR-0033): {@code marketdata:quote:close:{SYM}} expira en la
 * campana de apertura y {@code marketdata:quote:last:{SYM}} vive {@code max-age}.
 *
 * <p>Cualquier fallo de Redis se registra y se trata como "no hay dato", igual que {@code
 * RedisCacheErrorHandler}: la consulta sigue hacia los proveedores.
 */
@Component
@ConditionalOnProperty(
    name = "marketdata.quote.last-known.enabled",
    havingValue = "true",
    matchIfMissing = true)
@Slf4j
public class LastKnownQuoteRedisAdapter implements LastKnownQuotePort {

  static final String HELD_PREFIX = "marketdata:quote:close:";
  static final String LAST_PREFIX = "marketdata:quote:last:";

  private static final String PRICE = "price";
  private static final String FETCHED_AT = "fetchedAt";
  private static final String PROVIDER = "provider";
  private static final String SESSION_DATE = "sessionDate";
  private static final String SOURCE = "source";
  private static final String OPENS_AT = "opensAt";

  private final StringRedisTemplate redisTemplate;
  private final Clock clock;
  private final Duration maxAge;

  public LastKnownQuoteRedisAdapter(
      StringRedisTemplate redisTemplate,
      Clock clock,
      @Value("${marketdata.quote.last-known.max-age:P7D}") Duration maxAge) {
    this.redisTemplate = redisTemplate;
    this.clock = clock;
    this.maxAge = maxAge;
  }

  @Override
  public Optional<StockQuote> findHeld(String symbol) {
    try {
      Map<Object, Object> fields = redisTemplate.opsForHash().entries(HELD_PREFIX + symbol);
      // Si el EXPIREAT no llego a aplicarse la llave no caduca sola: la campana guardada manda.
      if (fields.isEmpty() || !opensInTheFuture(fields.get(OPENS_AT))) {
        return Optional.empty();
      }
      return Optional.of(toQuote(fields));
    } catch (RuntimeException ex) {
      log.warn("No se pudo leer el cierre retenido de {}: {}", symbol, ex.toString());
      return Optional.empty();
    }
  }

  @Override
  public Optional<StockQuote> findLast(String symbol) {
    try {
      Map<Object, Object> fields = redisTemplate.opsForHash().entries(LAST_PREFIX + symbol);
      return fields.isEmpty() ? Optional.empty() : Optional.of(toQuote(fields));
    } catch (RuntimeException ex) {
      log.warn("No se pudo leer el ultimo precio conocido de {}: {}", symbol, ex.toString());
      return Optional.empty();
    }
  }

  @Override
  public void saveLast(String symbol, StockQuote quote) {
    try {
      String key = LAST_PREFIX + symbol;
      redisTemplate.opsForHash().putAll(key, toFields(quote));
      redisTemplate.expire(key, maxAge);
    } catch (RuntimeException ex) {
      log.warn("No se pudo guardar el ultimo precio conocido de {}: {}", symbol, ex.toString());
    }
  }

  @Override
  public void hold(String symbol, StockQuote quote, HeldQuoteSource source, Instant opensAt) {
    try {
      String key = HELD_PREFIX + symbol;
      Map<String, String> fields = toFields(quote);
      fields.put(SOURCE, source.name());
      fields.put(OPENS_AT, opensAt.toString());
      redisTemplate.opsForHash().putAll(key, fields);
      redisTemplate.expireAt(key, opensAt);
    } catch (RuntimeException ex) {
      log.warn("No se pudo retener el cierre de {}: {}", symbol, ex.toString());
    }
  }

  private boolean opensInTheFuture(Object opensAt) {
    return opensAt != null && Instant.parse((String) opensAt).isAfter(clock.instant());
  }

  private Map<String, String> toFields(StockQuote quote) {
    Map<String, String> fields = new HashMap<>();
    fields.put(PRICE, quote.price().toPlainString());
    fields.put(FETCHED_AT, quote.fetchedAt().toString());
    fields.put(PROVIDER, quote.provider());
    // Siempre se escribe, aunque vacio, para no heredar la sesion de una escritura anterior.
    fields.put(SESSION_DATE, quote.sessionDate() == null ? "" : quote.sessionDate().toString());
    return fields;
  }

  private StockQuote toQuote(Map<Object, Object> fields) {
    String sessionDate = (String) fields.get(SESSION_DATE);
    return new StockQuote(
        new BigDecimal((String) fields.get(PRICE)),
        Instant.parse((String) fields.get(FETCHED_AT)),
        (String) fields.get(PROVIDER),
        sessionDate == null || sessionDate.isEmpty() ? null : LocalDate.parse(sessionDate));
  }
}
