package com.mx.cryptomonitor.portfolio.application.mapper;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioEntryResponse;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;

@Component
public class PortfolioEntryMapper {

  public PortfolioEntryResponse toResponse(PortfolioEntry entry) {
    return new PortfolioEntryResponse(
        entry.getPortfolioEntryId(),
        entry.getUserId(),
        entry.getAssetSymbol(),
        entry.getAssetType(),
        entry.getTotalQuantity(),
        entry.getTotalInvested(),
        entry.getAveragePricePerUnit(),
        entry.getLastTransactionPrice(),
        entry.getCurrentValue(),
        entry.getTotalProfitLoss(),
        entry.getLastUpdated(),
        entry.getCreatedAt(),
        entry.getUpdatedAt());
  }
}
