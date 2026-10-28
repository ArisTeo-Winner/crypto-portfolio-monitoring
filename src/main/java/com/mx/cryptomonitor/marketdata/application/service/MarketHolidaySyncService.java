package com.mx.cryptomonitor.marketdata.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayData;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.MarketHolidayEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketHolidayRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Ingesta del calendario US a {@code market_holiday} (ADR-0012, F4). Prueba los proveedores en
 * orden ({@code @Order}: Massive primario → Finnhub fallback) y usa el primero que devuelva datos;
 * inserta solo las fechas faltantes (preserva el seed verificado). Opt-in vía {@code
 * marketdata.holiday-sync.enabled}. BMV no se auto-ingesta (sembrado a mano).
 */
@Service
@Slf4j
public class MarketHolidaySyncService {

  private static final int NAME_MAX = 80;

  private final List<MarketHolidayProviderPort> providers;
  private final MarketHolidayRepository repository;
  private final boolean enabled;

  public MarketHolidaySyncService(
      List<MarketHolidayProviderPort> providers,
      MarketHolidayRepository repository,
      @Value("${marketdata.holiday-sync.enabled:false}") boolean enabled) {
    this.providers = providers;
    this.repository = repository;
    this.enabled = enabled;
  }

  @Scheduled(cron = "${marketdata.holiday-sync.cron:0 0 3 1 * *}")
  public void scheduledSync() {
    if (!enabled) {
      return;
    }
    try {
      syncUsHolidays();
    } catch (RuntimeException ex) {
      log.warn("Sincronización del calendario US falló; se usará lo sembrado", ex);
    }
  }

  /** Ingesta el calendario US con fallback; retorna cuántas fechas nuevas insertó. */
  public int syncUsHolidays() {
    List<MarketHolidayData> fetched = fetchWithFallback();
    if (fetched.isEmpty()) {
      log.info("Calendario US: ningún proveedor devolvió datos; se conserva el seed.");
      return 0;
    }
    Set<LocalDate> existing =
        repository.findByMarket(Market.US_EQUITY.name()).stream()
            .map(MarketHolidayEntity::getHolidayDate)
            .collect(Collectors.toSet());
    List<MarketHolidayEntity> toSave =
        fetched.stream()
            .filter(h -> h.market() == Market.US_EQUITY && h.date() != null)
            .filter(h -> !existing.contains(h.date()))
            .map(
                h ->
                    MarketHolidayEntity.builder()
                        .market(Market.US_EQUITY.name())
                        .holidayDate(h.date())
                        .name(truncate(h.name()))
                        .earlyClose(h.earlyClose())
                        .build())
            .toList();
    if (toSave.isEmpty()) {
      return 0;
    }
    repository.saveAll(toSave);
    log.info("Calendario US: {} fechas nuevas insertadas.", toSave.size());
    return toSave.size();
  }

  private List<MarketHolidayData> fetchWithFallback() {
    for (MarketHolidayProviderPort provider : providers) {
      try {
        List<MarketHolidayData> data = provider.fetchHolidays();
        if (!data.isEmpty()) {
          log.info(
              "Calendario US obtenido de {} ({} filas).", provider.providerName(), data.size());
          return data;
        }
      } catch (RuntimeException ex) {
        log.warn("Proveedor {} falló; se intenta el siguiente.", provider.providerName(), ex);
      }
    }
    return List.of();
  }

  private String truncate(String name) {
    String value = name == null ? "Holiday" : name;
    return value.length() > NAME_MAX ? value.substring(0, NAME_MAX) : value;
  }
}
