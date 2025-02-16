package com.mx.cryptomonitor.shared.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PortfolioEntryResponse(
	    UUID portfolioEntryId,
	    UUID userId,
	    String assetSymbol,
	    String assetType,
	    BigDecimal totalQuantity,
	    BigDecimal totalInvested,
	    BigDecimal averagePricePerUnit,
	    BigDecimal lastTransactionPrice,
	    BigDecimal currentValue,
	    BigDecimal totalProfitLoss,
	    LocalDateTime lastUpdated,
	    LocalDateTime createdAt,
	    LocalDateTime updatedAt
		) {

}
