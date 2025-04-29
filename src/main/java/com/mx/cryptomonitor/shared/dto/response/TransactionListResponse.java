package com.mx.cryptomonitor.shared.dto.response;

import java.util.List;

public record TransactionListResponse(
		List<TransactionResponse> transactions, String message
		) {

}
