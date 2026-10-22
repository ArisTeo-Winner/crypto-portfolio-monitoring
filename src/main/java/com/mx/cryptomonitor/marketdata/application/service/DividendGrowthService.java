package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.marketdata.application.dto.response.DividendGrowthResponse;
import com.mx.cryptomonitor.marketdata.application.port.in.GetDividendGrowthUseCase;
import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthCalculator;
import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthCalculator.DatedDps;
import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthResult;
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
  private final DividendGrowthCalculator growthCalculator = new DividendGrowthCalculator();

  @Override
  public DividendGrowthResponse growthFor(String symbol) {
    if (!StringUtils.hasText(symbol)) {
      return toResponse(DividendGrowthResult.empty(""), null);
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
    return toResponse(growthCalculator.compute(normalized, dividends, currentYear()), currency);
  }

  protected int currentYear() {
    return Year.now(ZoneOffset.UTC).getValue();
  }

  private DividendGrowthResponse toResponse(DividendGrowthResult result, String currency) {
    return new DividendGrowthResponse(
        result.symbol(),
        result.annualDividends().stream()
            .map(a -> new DividendGrowthResponse.AnnualDividendPoint(a.year(), a.totalDps()))
            .toList(),
        result.cagr1y(),
        result.cagr3y(),
        result.cagr5y(),
        result.cagr10y(),
        currency);
  }
}
