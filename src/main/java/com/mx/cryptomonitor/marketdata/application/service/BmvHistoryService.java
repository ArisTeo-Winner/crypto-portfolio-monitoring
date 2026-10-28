package com.mx.cryptomonitor.marketdata.application.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.BmvMarketDataPort;
import com.mx.cryptomonitor.marketdata.domain.model.BmvHistoricalPoint;
import com.mx.cryptomonitor.marketdata.domain.model.BmvPriceHistoryEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.BmvPriceHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cache incremental de precios historicos BMV — evita quemar creditos de DataBursatil
 * re-descargando rangos ya cacheados.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BmvHistoryService {

  private static final String PROVIDER = "DATABURSATIL";

  private final BmvMarketDataPort databursatil;
  private final BmvPriceHistoryRepository historyRepository;

  public List<BmvHistoricalPoint> getHistory(String symbol, LocalDate from, LocalDate to) {
    List<BmvPriceHistoryEntity> cached =
        historyRepository.findByEmisoraSerieAndTradeDateBetween(symbol, from, to);

    if (cached.isEmpty()) {
      fetchAndCache(symbol, from, to);
    } else {
      LocalDate firstCached =
          cached.stream()
              .map(BmvPriceHistoryEntity::getTradeDate)
              .min(Comparator.naturalOrder())
              .orElseThrow();
      LocalDate lastCached =
          cached.stream()
              .map(BmvPriceHistoryEntity::getTradeDate)
              .max(Comparator.naturalOrder())
              .orElseThrow();

      boolean hasLeadingGap = firstCached.isAfter(from);
      boolean hasTrailingGap = lastCached.isBefore(to);

      if (hasLeadingGap) {
        fetchAndCache(symbol, from, firstCached.minusDays(1));
      }
      if (hasTrailingGap) {
        fetchAndCache(symbol, lastCached.plusDays(1), to);
      }
      if (!hasLeadingGap && !hasTrailingGap) {
        log.debug(
            "BmvHistoryService: rango {}..{} para {} ya cacheado, sin llamada a DataBursatil",
            from,
            to,
            symbol);
      }
    }

    return historyRepository.findByEmisoraSerieAndTradeDateBetween(symbol, from, to).stream()
        .map(this::toHistoricalPoint)
        .sorted(Comparator.comparing(BmvHistoricalPoint::date))
        .toList();
  }

  private void fetchAndCache(String symbol, LocalDate from, LocalDate to) {
    Map<LocalDate, BmvHistoricalPoint> fetched = databursatil.getHistory(symbol, from, to).block();
    if (fetched == null || fetched.isEmpty()) {
      return;
    }
    OffsetDateTime cachedAt = OffsetDateTime.now(ZoneOffset.UTC);
    fetched
        .values()
        .forEach(
            point ->
                historyRepository.upsert(
                    UUID.randomUUID(),
                    symbol,
                    point.date(),
                    point.closePrice(),
                    point.amountTraded(),
                    PROVIDER,
                    cachedAt));
  }

  private BmvHistoricalPoint toHistoricalPoint(BmvPriceHistoryEntity entity) {
    return new BmvHistoricalPoint(
        entity.getTradeDate(), entity.getClosePrice(), entity.getAmountTraded());
  }
}
