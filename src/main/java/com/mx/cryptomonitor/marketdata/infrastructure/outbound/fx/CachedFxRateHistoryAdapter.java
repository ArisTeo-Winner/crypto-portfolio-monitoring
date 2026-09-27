package com.mx.cryptomonitor.marketdata.infrastructure.outbound.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRateHistoryPort;
import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.UsdMxnFxRateHistoryProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.FxRateDailyEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.FxRateDailyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tipo de cambio USD/MXN por fecha de operacion (trade-date FX, ADR-0009). El FIX de un dia es
 * inmutable, asi que se cachea permanentemente en {@code fx_rate_daily}. Ante un dia sin cobertura
 * cercana, trae el rango desde el proveedor historico (Banxico SF43718) y lo persiste. Devuelve el
 * FIX de la fecha pedida o el vigente mas reciente anterior (fin de semana/feriado). Si no hay dato
 * historico (ni tras el fetch), cae a la tasa spot actual ({@link FxRatePort}) como ultimo recurso.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CachedFxRateHistoryAdapter implements FxRateHistoryPort {

  private static final String USD_MXN_TICKER = "USDMXN";
  // Ventana para considerar que ya hay un FIX habil cercano cacheado (evita refetch en fines de
  // semana/feriados una vez poblado el rango).
  private static final int COVERAGE_LOOKBACK_DAYS = 4;
  // Ventana a traer del proveedor ante un miss (cubre feriados largos).
  private static final int FETCH_LOOKBACK_DAYS = 10;

  private final FxRateDailyRepository fxRateDailyRepository;
  private final UsdMxnFxRateHistoryProviderPort fxRateHistoryProvider;
  private final FxRatePort fxRatePort;

  @Override
  public Optional<BigDecimal> usdMxnRateOn(LocalDate date) {
    if (date == null) {
      return Optional.empty();
    }
    ensureCovered(date);
    Optional<BigDecimal> historical =
        fxRateDailyRepository
            .findFirstByTickerAndRateDateLessThanEqualOrderByRateDateDesc(USD_MXN_TICKER, date)
            .map(FxRateDailyEntity::getRate);
    return historical.or(fxRatePort::usdMxnRate);
  }

  private void ensureCovered(LocalDate date) {
    boolean alreadyCovered =
        !fxRateDailyRepository
            .findByTickerAndRateDateBetween(
                USD_MXN_TICKER, date.minusDays(COVERAGE_LOOKBACK_DAYS), date)
            .isEmpty();
    if (alreadyCovered) {
      return;
    }
    try {
      Map<LocalDate, BigDecimal> fetched =
          fxRateHistoryProvider.fetchUsdMxnRates(date.minusDays(FETCH_LOOKBACK_DAYS), date);
      persistMissing(fetched);
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo traer el historico USD/MXN alrededor de {}; se usara cache o tasa spot",
          date,
          ex);
    }
  }

  private void persistMissing(Map<LocalDate, BigDecimal> fetched) {
    if (fetched.isEmpty()) {
      return;
    }
    LocalDate min = fetched.keySet().stream().min(LocalDate::compareTo).orElseThrow();
    LocalDate max = fetched.keySet().stream().max(LocalDate::compareTo).orElseThrow();
    Set<LocalDate> existing =
        fxRateDailyRepository.findByTickerAndRateDateBetween(USD_MXN_TICKER, min, max).stream()
            .map(FxRateDailyEntity::getRateDate)
            .collect(Collectors.toSet());

    List<FxRateDailyEntity> toSave =
        fetched.entrySet().stream()
            .filter(entry -> !existing.contains(entry.getKey()))
            .map(
                entry ->
                    FxRateDailyEntity.builder()
                        .ticker(USD_MXN_TICKER)
                        .rateDate(entry.getKey())
                        .rate(entry.getValue())
                        .provider("BANXICO")
                        .build())
            .toList();

    if (toSave.isEmpty()) {
      return;
    }
    try {
      fxRateDailyRepository.saveAll(toSave);
    } catch (DataIntegrityViolationException ex) {
      // Otra reconciliacion concurrente ya inserto estas fechas (unique ticker+rate_date); benigno.
      log.debug("Escritura concurrente de fx_rate_daily; filas ya presentes", ex);
    }
  }
}
