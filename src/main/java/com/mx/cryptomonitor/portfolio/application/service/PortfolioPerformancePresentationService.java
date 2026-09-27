package com.mx.cryptomonitor.portfolio.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.dto.response.MoneyPresentation;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryMeta;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse.SeriesPoint;
import com.mx.cryptomonitor.portfolio.application.dto.response.ReturnMetricsResponse;
import com.mx.cryptomonitor.portfolio.application.service.MoneyPresentationConverter.PresentationContext;
import com.mx.cryptomonitor.portfolio.domain.model.PresentationCurrency;

import lombok.RequiredArgsConstructor;

/**
 * Presenta las respuestas de series (holdings-performance e history) en la moneda del usuario
 * (ADR-0010): convierte los importes (valores de serie, costo base, ganancia absoluta, invertido) y
 * adjunta el envelope. Los retornos (TWR/MWR) y porcentajes son invariantes de moneda y no se
 * tocan.
 */
@Service
@RequiredArgsConstructor
public class PortfolioPerformancePresentationService {

  private final MoneyPresentationConverter moneyPresentationConverter;

  public PortfolioHoldingsPerformanceResponse present(
      PortfolioHoldingsPerformanceResponse base, PresentationCurrency target) {
    PresentationContext ctx = moneyPresentationConverter.resolve(target);
    MoneyPresentation envelope = moneyPresentationConverter.toEnvelope(ctx);

    List<SeriesPoint> series =
        base.series().stream()
            .map(
                p ->
                    new SeriesPoint(p.time(), moneyPresentationConverter.toDisplay(p.value(), ctx)))
            .toList();

    return new PortfolioHoldingsPerformanceResponse(
        series,
        base.isProfit(),
        moneyPresentationConverter.toDisplay(base.allTimeProfit(), ctx),
        base.allTimeProfitPercent(),
        moneyPresentationConverter.toDisplay(base.costBasis(), ctx),
        base.firstTransactionDate(),
        envelope);
  }

  public PortfolioHistoryResponse present(
      PortfolioHistoryResponse base, PresentationCurrency target) {
    PresentationContext ctx = moneyPresentationConverter.resolve(target);
    MoneyPresentation envelope = moneyPresentationConverter.toEnvelope(ctx);

    List<PortfolioHistoryPointResponse> series =
        base.series().stream()
            .map(
                p ->
                    new PortfolioHistoryPointResponse(
                        p.time(), moneyPresentationConverter.toDisplay(p.value(), ctx)))
            .toList();

    PortfolioHistoryMeta baseMeta = base.meta();
    ReturnMetricsResponse returns = presentReturns(baseMeta.returns(), ctx);
    PortfolioHistoryMeta meta =
        new PortfolioHistoryMeta(
            baseMeta.range(),
            baseMeta.resolution(),
            baseMeta.from(),
            baseMeta.to(),
            ctx.currency().name(),
            baseMeta.points(),
            returns,
            baseMeta.partial(),
            baseMeta.unavailableSymbols(),
            envelope);
    return new PortfolioHistoryResponse(meta, series);
  }

  private ReturnMetricsResponse presentReturns(
      ReturnMetricsResponse returns, PresentationContext ctx) {
    if (returns == null) {
      return null;
    }
    return new ReturnMetricsResponse(
        returns.twr(),
        returns.mwr(),
        moneyPresentationConverter.toDisplay(returns.absoluteGain(), ctx),
        moneyPresentationConverter.toDisplay(returns.totalInvested(), ctx));
  }
}
