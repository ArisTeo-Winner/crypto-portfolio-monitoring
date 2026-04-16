package com.mx.cryptomonitor.transaction.application.dto.response;

import java.util.List;

public record TransactionListResponse(List<TransactionResponse> transactions, String message) {}
