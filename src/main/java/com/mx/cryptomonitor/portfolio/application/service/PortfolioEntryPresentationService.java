package com.mx.cryptomonitor.portfolio.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.dto.response.MoneyPresentation;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioEntryResponse;
import com.mx.cryptomonitor.portfolio.application.mapper.PortfolioEntryMapper;
import com.mx.cryptomonitor.portfolio.application.service.MoneyPresentationConverter.PresentationContext;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.model.PresentationCurrency;

import lombok.RequiredArgsConstructor;

/**
 * Ensambla las respuestas de {@code /me/portfolio} en la moneda de presentacion del usuario
 * (ADR-0010): mapea la proyeccion base (USD) y convierte solo los importes monetarios; la cantidad
 * no es dinero y queda intacta; los porcentajes no se calculan aqui. La tasa se resuelve una sola
 * vez.
 */
@Service
@RequiredArgsConstructor
public class PortfolioEntryPresentationService {

  private final PortfolioEntryMapper portfolioEntryMapper;
  private final MoneyPresentationConverter moneyPresentationConverter;

  public List<PortfolioEntryResponse> present(
      List<PortfolioEntry> entries, PresentationCurrency target) {
    PresentationContext context = moneyPresentationConverter.resolve(target);
    MoneyPresentation envelope = moneyPresentationConverter.toEnvelope(context);
    return entries.stream().map(entry -> toDisplayResponse(entry, context, envelope)).toList();
  }

  public PortfolioEntryResponse present(PortfolioEntry entry, PresentationCurrency target) {
    PresentationContext context = moneyPresentationConverter.resolve(target);
    return toDisplayResponse(entry, context, moneyPresentationConverter.toEnvelope(context));
  }

  private PortfolioEntryResponse toDisplayResponse(
      PortfolioEntry entry, PresentationContext context, MoneyPresentation envelope) {
    PortfolioEntryResponse base = portfolioEntryMapper.toResponse(entry);
    return new PortfolioEntryResponse(
        base.portfolioEntryId(),
        base.userId(),
        base.assetSymbol(),
        base.assetType(),
        base.totalQuantity(),
        moneyPresentationConverter.toDisplay(base.totalInvested(), context),
        moneyPresentationConverter.toDisplay(base.averagePricePerUnit(), context),
        moneyPresentationConverter.toDisplay(base.lastTransactionPrice(), context),
        moneyPresentationConverter.toDisplay(base.currentValue(), context),
        moneyPresentationConverter.toDisplay(base.totalProfitLoss(), context),
        base.lastUpdated(),
        base.createdAt(),
        base.updatedAt(),
        envelope);
  }
}
