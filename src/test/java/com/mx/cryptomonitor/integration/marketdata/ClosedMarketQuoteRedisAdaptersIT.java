package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.mx.cryptomonitor.marketdata.domain.model.HeldQuoteSource;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState;
import com.mx.cryptomonitor.marketdata.domain.model.StockQuote;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.redis.LastKnownQuoteRedisAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.redis.MarketStateRedisAdapter;

/** Llaves Redis de ADR-0033 contra un Redis real: contenido, vigencia y expiración. */
@Testcontainers(disabledWithoutDocker = true)
class ClosedMarketQuoteRedisAdaptersIT {

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  private static final Duration MAX_AGE = Duration.ofDays(7);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    redisTemplate.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushAll();
              return null;
            });
  }

  @AfterEach
  void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  void heldCloseIsReadableAndItsTtlCountsDownToTheBell() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant opensAt = now.plus(Duration.ofHours(65));
    LastKnownQuoteRedisAdapter adapter = quoteAdapter(Clock.systemUTC());
    StockQuote quote =
        new StockQuote(
            new BigDecimal("356.26999"), now, "twelvedata-eod", LocalDate.of(2026, 11, 13));

    adapter.hold("AAPL", quote, HeldQuoteSource.OFFICIAL_EOD, opensAt);

    assertThat(adapter.findHeld("AAPL")).contains(quote);
    assertThat(redisTemplate.opsForHash().get("marketdata:quote:close:AAPL", "source"))
        .isEqualTo("OFFICIAL_EOD");
    assertThat(redisTemplate.getExpire("marketdata:quote:close:AAPL", TimeUnit.SECONDS))
        .isBetween(Duration.ofHours(65).toSeconds() - 60, Duration.ofHours(65).toSeconds());
  }

  @Test
  void heldCloseDisappearsAtTheBell() throws Exception {
    Instant now = Instant.now();
    LastKnownQuoteRedisAdapter adapter = quoteAdapter(Clock.systemUTC());

    adapter.hold(
        "KO",
        new StockQuote(new BigDecimal("99.27"), now, "eodhd", null),
        HeldQuoteSource.SETTLED_CHAIN,
        now.plusSeconds(2));

    assertThat(adapter.findHeld("KO")).isPresent();
    Thread.sleep(3_000);
    assertThat(adapter.findHeld("KO")).isEmpty();
    assertThat(redisTemplate.hasKey("marketdata:quote:close:KO")).isFalse();
  }

  @Test
  void heldCloseWhoseBellAlreadyRangIsIgnoredEvenIfTheKeySurvived() {
    Instant bell = Instant.parse("2026-11-16T14:30:00Z");
    LastKnownQuoteRedisAdapter writer = quoteAdapter(Clock.systemUTC());
    writer.hold(
        "MELI",
        new StockQuote(new BigDecimal("1810.15"), bell.minusSeconds(3600), "eodhd", null),
        HeldQuoteSource.SETTLED_CHAIN,
        Instant.now().plus(Duration.ofHours(1)));
    // Simula un EXPIREAT que no llego a aplicarse: la llave sigue viva pero su campana ya paso.
    redisTemplate.opsForHash().put("marketdata:quote:close:MELI", "opensAt", bell.toString());
    redisTemplate.persist("marketdata:quote:close:MELI");

    LastKnownQuoteRedisAdapter reader =
        quoteAdapter(Clock.fixed(bell.plusSeconds(1), ZoneOffset.UTC));

    assertThat(reader.findHeld("MELI")).isEmpty();
  }

  @Test
  void lastKnownPriceIsKeptForMaxAgeAndDoesNotInheritAnOlderSessionDate() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    LastKnownQuoteRedisAdapter adapter = quoteAdapter(Clock.systemUTC());

    adapter.saveLast(
        "NVDA",
        new StockQuote(
            new BigDecimal("140.10"), now, "twelvedata-eod", LocalDate.of(2026, 11, 13)));
    StockQuote latest = new StockQuote(new BigDecimal("141.55"), now, "eodhd", null);
    adapter.saveLast("NVDA", latest);

    assertThat(adapter.findLast("NVDA")).contains(latest);
    assertThat(redisTemplate.getExpire("marketdata:quote:last:NVDA", TimeUnit.SECONDS))
        .isBetween(MAX_AGE.toSeconds() - 60, MAX_AGE.toSeconds());
    assertThat(adapter.findHeld("NVDA")).isEmpty();
  }

  @Test
  void unknownSymbolHasNoQuotes() {
    LastKnownQuoteRedisAdapter adapter = quoteAdapter(Clock.systemUTC());

    assertThat(adapter.findHeld("ZZZZ")).isEmpty();
    assertThat(adapter.findLast("ZZZZ")).isEmpty();
  }

  @Test
  void marketStateIsRememberedUntilItsExpiry() {
    Instant opensAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofHours(10));
    MarketStateRedisAdapter adapter = new MarketStateRedisAdapter(redisTemplate, Clock.systemUTC());

    adapter.save(Market.US_EQUITY, RemoteMarketState.closedUntil(opensAt), opensAt);
    adapter.save(
        Market.MX_BMV, RemoteMarketState.unavailable(), Instant.now().plus(Duration.ofHours(1)));

    assertThat(adapter.find(Market.US_EQUITY)).contains(RemoteMarketState.closedUntil(opensAt));
    assertThat(adapter.find(Market.MX_BMV)).contains(RemoteMarketState.unavailable());
    assertThat(redisTemplate.getExpire("marketdata:market-state:US_EQUITY", TimeUnit.SECONDS))
        .isBetween(Duration.ofHours(10).toSeconds() - 60, Duration.ofHours(10).toSeconds());
  }

  @Test
  void expiredMarketStateIsIgnored() {
    Instant expiry = Instant.parse("2026-11-16T14:30:00Z");
    MarketStateRedisAdapter writer = new MarketStateRedisAdapter(redisTemplate, Clock.systemUTC());
    writer.save(
        Market.US_EQUITY, RemoteMarketState.open(), Instant.now().plus(Duration.ofHours(1)));
    redisTemplate
        .opsForHash()
        .put("marketdata:market-state:US_EQUITY", "expiresAt", expiry.toString());

    MarketStateRedisAdapter reader =
        new MarketStateRedisAdapter(redisTemplate, Clock.fixed(expiry, ZoneOffset.UTC));

    assertThat(reader.find(Market.US_EQUITY)).isEmpty();
    assertThat(reader.find(Market.MX_BMV)).isEmpty();
  }

  private LastKnownQuoteRedisAdapter quoteAdapter(Clock clock) {
    return new LastKnownQuoteRedisAdapter(redisTemplate, clock, MAX_AGE);
  }
}
