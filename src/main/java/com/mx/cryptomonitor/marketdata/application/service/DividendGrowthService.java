package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.marketdata.application.dto.response.DividendGrowthResponse;
import com.mx.cryptomonitor.marketdata.application.port.in.GetDividendGrowthUseCase;
import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthCalculator;
import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthCalculator.DatedDps;
import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthResult;
import com.mx.cryptomonitor.marketdata.domain.model.AssetDividendStatusEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.DividendActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.DividendEligibilityStatus;
import com.mx.cryptomonitor.marketdata.domain.repository.AssetDividendStatusRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.DividendActionDetailRepository;

import lombok.RequiredArgsConstructor;

/**
 * Crecimiento del dividendo de una seguridad (ADR-0014 F5): lee los DPS recurring detectados,
 * delega el agregado anual y la CAGR al dominio ({@link DividendGrowthCalculator}) y mapea a
 * respuesta. El anio en curso se excluye (incompleto) via {@link #currentYear()}.
 */
@Service
@RequiredArgsConstructor
public class DividendGrowthService implements GetDividendGrowthUseCase {

  private final DividendActionDetailRepository dividendDetailRepository;
  private final AssetDividendStatusRepository dividendStatusRepository;
  private final CorporateActionRepository corporateActionRepository;
  private final DividendGrowthCalculator growthCalculator = new DividendGrowthCalculator();

  @Override
  public DividendGrowthResponse growthFor(String symbol) {
    if (!StringUtils.hasText(symbol)) {
      return toResponse(
          DividendGrowthResult.empty(""),
          null,
          DividendEligibilityStatus.UNKNOWN.name(),
          null,
          null);
    }
    String normalized = symbol.trim().toUpperCase();
    List<Object[]> rows = dividendDetailRepository.findRecurringDpsBySymbol(normalized);
    List<DatedDps> dividends =
        rows.stream().map(row -> new DatedDps((LocalDate) row[0], (BigDecimal) row[1])).toList();
    String currency =
        rows.stream()
            .map(row -> (String) row[2])
            .filter(StringUtils::hasText)
            .reduce((first, second) -> second) // ultima no nula (moneda vigente de la seguridad)
            .orElse(null);
    // ADR-0025 F4: estado de elegibilidad persistido; sin fila = UNKNOWN (aún no verificado).
    Optional<AssetDividendStatusEntity> status =
        dividendStatusRepository.findByAssetSymbol(normalized);
    String eligibility =
        status.map(e -> e.getStatus().name()).orElse(DividendEligibilityStatus.UNKNOWN.name());
    OffsetDateTime lastCheckedAt =
        status.map(AssetDividendStatusEntity::getLastCheckedAt).orElse(null);
    return toResponse(
        growthCalculator.compute(normalized, dividends, currentYear()),
        currency,
        eligibility,
        lastCheckedAt,
        nextDividendFor(normalized));
  }

  protected int currentYear() {
    return Year.now(ZoneOffset.UTC).getValue();
  }

  protected LocalDate today() {
    return LocalDate.now(ZoneOffset.UTC);
  }

  /**
   * ADR-0026: proximo dividendo anunciado (pago futuro mas cercano, aun no pagado) con su detalle,
   * o {@code null} si no hay uno.
   */
  private DividendGrowthResponse.NextDividend nextDividendFor(String symbol) {
    return corporateActionRepository
        .findFirstByAssetSymbolAndActionTypeAndPaymentDateGreaterThanEqualOrderByPaymentDateAsc(
            symbol, CorporateActionType.DIVIDEND, today())
        .map(this::toNextDividend)
        .orElse(null);
  }

  private DividendGrowthResponse.NextDividend toNextDividend(CorporateActionEntity action) {
    DividendActionDetailEntity detail =
        dividendDetailRepository.findById(action.getId()).orElse(null);
    if (detail == null) {
      return null;
    }
    return new DividendGrowthResponse.NextDividend(
        action.getPivotalDate(),
        action.getPaymentDate(),
        detail.getAmountPerShare(),
        detail.getCurrency(),
        detail.getFrequency());
  }

  private DividendGrowthResponse toResponse(
      DividendGrowthResult result,
      String currency,
      String eligibilityStatus,
      OffsetDateTime lastCheckedAt,
      DividendGrowthResponse.NextDividend nextDividend) {
    return new DividendGrowthResponse(
        result.symbol(),
        result.annualDividends().stream()
            .map(a -> new DividendGrowthResponse.AnnualDividendPoint(a.year(), a.totalDps()))
            .toList(),
        result.cagr1y(),
        result.cagr3y(),
        result.cagr5y(),
        result.cagr10y(),
        currency,
        eligibilityStatus,
        lastCheckedAt,
        nextDividend);
  }
}
