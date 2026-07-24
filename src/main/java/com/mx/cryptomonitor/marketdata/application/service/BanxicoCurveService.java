package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.marketdata.application.port.out.BanxicoCurveCachePort;
import com.mx.cryptomonitor.marketdata.application.port.out.GovBondRatePort;
import com.mx.cryptomonitor.marketdata.domain.repository.BanxicoCetesRateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cache de la curva CETES de Banxico (Redis -> Postgres -> Banxico), con refresco semanal. La
 * subasta de CETES es semanal (martes), asi que la curva no se consulta a Banxico en cada valuacion
 * a mercado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BanxicoCurveService {

  private final GovBondRatePort banxicoRateAdapter;
  private final BanxicoCetesRateRepository rateRepository;
  private final BanxicoCurveCachePort curveCache;

  @Value("${banxico.curve.refresh.enabled:true}")
  private boolean scheduledRefreshEnabled;

  @Transactional
  public Map<Integer, BigDecimal> getCurve() {
    Map<Integer, BigDecimal> cached = curveCache.readCurve();
    if (!cached.isEmpty()) {
      return cached;
    }

    Map<Integer, BigDecimal> fromDb = readFromPostgres();
    if (!fromDb.isEmpty()) {
      curveCache.writeCurve(fromDb, curveCache.readAuctionDate().orElse(LocalDate.now()));
      return fromDb;
    }

    try {
      return fetchFromProviderAndPersist();
    } catch (RuntimeException ex) {
      log.error("BanxicoCurveService: Banxico no disponible y no hay curva en cache", ex);
      return Map.of();
    }
  }

  public Optional<BigDecimal> getRate(int termDays) {
    return GovBondRatePort.nearestRate(getCurve(), termDays);
  }

  @Scheduled(cron = "${banxico.curve.refresh.cron:0 0 12 * * WED}")
  @Transactional
  public void refreshCurve() {
    if (!scheduledRefreshEnabled) {
      return;
    }
    try {
      fetchFromProviderAndPersist();
      log.info("BanxicoCurveService: curva CETES actualizada desde Banxico");
    } catch (RuntimeException ex) {
      log.error(
          "BanxicoCurveService: fallo al refrescar la curva CETES, se conserva la curva previa",
          ex);
    }
  }

  private Map<Integer, BigDecimal> fetchFromProviderAndPersist() {
    Map<Integer, BigDecimal> curve = banxicoRateAdapter.getCetesCurve();
    if (curve == null || curve.isEmpty()) {
      throw new IllegalStateException("Banxico devolvio una curva CETES vacia");
    }
    LocalDate auctionDate = LocalDate.now();
    OffsetDateTime fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);
    curve.forEach(
        (term, rate) ->
            rateRepository.upsert(UUID.randomUUID(), term, rate, auctionDate, fetchedAt));
    curveCache.writeCurve(curve, auctionDate);
    return curve;
  }

  private Map<Integer, BigDecimal> readFromPostgres() {
    Optional<LocalDate> latestAuctionDate = rateRepository.findLatestAuctionDate();
    if (latestAuctionDate.isEmpty()) {
      return Map.of();
    }
    Map<Integer, BigDecimal> curve = new TreeMap<>();
    rateRepository
        .findByAuctionDate(latestAuctionDate.get())
        .forEach(entity -> curve.put(entity.getTermDays(), entity.getRate()));
    return curve;
  }
}
