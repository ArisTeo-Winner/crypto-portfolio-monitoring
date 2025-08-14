package com.mx.cryptomonitor.shared.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalPriceResonseDTO(
		String symbol,
		LocalDate date,
		BigDecimal closePrice) {

}
