package com.mx.cryptomonitor.shared.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.User;

public record TransactionRequest(
		UUID userId,
		UUID portfolioEntryId,
        String assetSymbol, //BTC
        String assetType, // CRYPTO o STOCK
        String transactionType, // BUY o SELL
        BigDecimal quantity,
        BigDecimal pricePerUnit,
        BigDecimal totalValue,
        LocalDateTime transactionDate,
        BigDecimal fee,
        BigDecimal priceAtTransaction,
        String notes
		) {
	

}
