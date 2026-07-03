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
    LocalDate lastCached = findLastCachedDate(symbol, from, to);

    if (lastCached == null) {
      fetchAndCache(symbol, from, to);
    } else if (lastCached.isBefore(to)) {
      fetchAndCache(symbol, lastCached.plusDays(1), to);
    } else {
      log.debug(
          "BmvHistoryService: rango {}..{} para {} ya cacheado, sin llamada a DataBursatil",
          from,
          to,
          symbol);
    }

    return historyRepository.findByEmisoraSerieAndTradeDateBetween(symbol, from, to).stream()
        .map(this::toHistoricalPoint)
        .sorted(Comparator.comparing(BmvHistoricalPoint::date))
        .toList();
  }

  private LocalDate findLastCachedDate(String symbol, LocalDate from, LocalDate to) {
    return historyRepository.findByEmisoraSerieAndTradeDateBetween(symbol, from, to).stream()
        .map(BmvPriceHistoryEntity::getTradeDate)
        .max(Comparator.naturalOrder())
        .orElse(null);
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
