package com.mx.cryptomonitor.marketdata.infrastructure.outbound.split;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitPort;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.StockSplitEntity;
import com.mx.cryptomonitor.marketdata.domain.model.StockSplitSyncEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.StockSplitRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.StockSplitSyncRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Splits de un ticker (ADR-0011). Lectura en capas: Redis (read-through) -> Postgres (fuente de
 * verdad, fetch-on-miss contra Massive) -> se repuebla Redis con TTL. Un hit en Redis no toca ni
 * Postgres ni el API externo (paso 2 del flujo de captura). Postgres sigue siendo la verdad: si
 * Redis se cae o evicta, se lee de DB y se repuebla. Redis y ObjectMapper son opcionales; sin ellos
 * el adapter funciona solo con Postgres.
 */
@Component
@Slf4j
public class CachedStockSplitAdapter implements StockSplitPort {

  private static final String CACHE_KEY_PREFIX = "marketdata:splits:";

  private final StockSplitRepository stockSplitRepository;
  private final StockSplitSyncRepository stockSplitSyncRepository;
  private final StockSplitProviderPort stockSplitProvider;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${marketdata.splits.cache-ttl-hours:24}")
  private long cacheTtlHours;

  public CachedStockSplitAdapter(
      StockSplitRepository stockSplitRepository,
      StockSplitSyncRepository stockSplitSyncRepository,
      StockSplitProviderPort stockSplitProvider,
      @Nullable StringRedisTemplate redisTemplate,
      @Nullable ObjectMapper objectMapper) {
    this.stockSplitRepository = stockSplitRepository;
    this.stockSplitSyncRepository = stockSplitSyncRepository;
    this.stockSplitProvider = stockSplitProvider;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<StockSplitData> splitsFor(String ticker) {
    if (ticker == null || ticker.isBlank()) {
      return List.of();
    }
    String normalized = ticker.trim().toUpperCase();
    List<StockSplitData> cached = readCache(normalized);
    if (cached != null) {
      return cached;
    }
    if (!isFresh(normalized)) {
      syncFromProvider(normalized);
    }
    List<StockSplitData> result =
        stockSplitRepository.findByTickerOrderByExecutionDateDesc(normalized).stream()
            .map(
                e ->
                    new StockSplitData(e.getTicker(), e.getExecutionDate(), e.getShareMultiplier()))
            .toList();
    writeCache(normalized, result);
    return result;
  }

  @Nullable
  private List<StockSplitData> readCache(String ticker) {
    if (redisTemplate == null || objectMapper == null) {
      return null;
    }
    try {
      String json = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + ticker);
      if (json == null) {
        return null;
      }
      return objectMapper.readValue(json, new TypeReference<List<StockSplitData>>() {});
    } catch (RuntimeException | JsonProcessingException ex) {
      log.debug("Lectura de cache Redis de splits para {} fallo; se recurre a DB", ticker, ex);
      return null;
    }
  }

  private void writeCache(String ticker, List<StockSplitData> splits) {
    if (redisTemplate == null || objectMapper == null) {
      return;
    }
    try {
      redisTemplate
          .opsForValue()
          .set(
              CACHE_KEY_PREFIX + ticker,
              objectMapper.writeValueAsString(splits),
              Duration.ofHours(cacheTtlHours));
    } catch (RuntimeException | JsonProcessingException ex) {
      log.debug("Escritura de cache Redis de splits para {} fallo; se ignora", ticker, ex);
    }
  }

  private boolean isFresh(String ticker) {
    return stockSplitSyncRepository
        .findByTicker(ticker)
        .map(StockSplitSyncEntity::getSyncedAt)
        .filter(at -> at.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusHours(cacheTtlHours)))
        .isPresent();
  }

  private void syncFromProvider(String ticker) {
    try {
      List<StockSplitData> fetched = stockSplitProvider.fetchSplits(ticker);
      persistMissing(ticker, fetched);
      stockSplitSyncRepository.save(
          StockSplitSyncEntity.builder()
              .ticker(ticker)
              .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
              .build());
    } catch (RuntimeException ex) {
      log.warn(
          "Sincronizacion de splits para {} fallo; se usara lo cacheado si existe", ticker, ex);
    }
  }

  private void persistMissing(String ticker, List<StockSplitData> fetched) {
    if (fetched.isEmpty()) {
      return;
    }
    Set<java.time.LocalDate> existing =
        stockSplitRepository.findByTickerOrderByExecutionDateDesc(ticker).stream()
            .map(StockSplitEntity::getExecutionDate)
            .collect(Collectors.toSet());
    List<StockSplitEntity> toSave =
        fetched.stream()
            .filter(s -> !existing.contains(s.executionDate()))
            .map(
                s ->
                    StockSplitEntity.builder()
                        .ticker(ticker)
                        .executionDate(s.executionDate())
                        .shareMultiplier(s.shareMultiplier())
                        .provider("MASSIVE")
                        .build())
            .toList();
    if (toSave.isEmpty()) {
      return;
    }
    try {
      stockSplitRepository.saveAll(toSave);
    } catch (DataIntegrityViolationException ex) {
      log.debug("Escritura concurrente de stock_split para {}; filas ya presentes", ticker, ex);
    }
  }
}
