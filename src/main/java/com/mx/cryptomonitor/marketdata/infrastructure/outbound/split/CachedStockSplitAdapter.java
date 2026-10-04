package com.mx.cryptomonitor.marketdata.infrastructure.outbound.split;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitPort;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.StockSplitEntity;
import com.mx.cryptomonitor.marketdata.domain.model.StockSplitSyncEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.StockSplitRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.StockSplitSyncRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Splits de un ticker leidos de {@code stock_split} (ADR-0011). Fetch-on-miss: si el ticker no se
 * ha sincronizado dentro del TTL, trae su historico del proveedor (Massive) y lo persiste; luego
 * sirve siempre de la DB. Los splits son inmutables => cache barata. Nunca llama al API en cada
 * lectura.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CachedStockSplitAdapter implements StockSplitPort {

  private final StockSplitRepository stockSplitRepository;
  private final StockSplitSyncRepository stockSplitSyncRepository;
  private final StockSplitProviderPort stockSplitProvider;

  @Value("${marketdata.splits.cache-ttl-hours:24}")
  private long cacheTtlHours;

  @Override
  public List<StockSplitData> splitsFor(String ticker) {
    if (ticker == null || ticker.isBlank()) {
      return List.of();
    }
    String normalized = ticker.trim().toUpperCase();
    if (!isFresh(normalized)) {
      syncFromProvider(normalized);
    }
    return stockSplitRepository.findByTickerOrderByExecutionDateDesc(normalized).stream()
        .map(e -> new StockSplitData(e.getTicker(), e.getExecutionDate(), e.getShareMultiplier()))
        .toList();
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
