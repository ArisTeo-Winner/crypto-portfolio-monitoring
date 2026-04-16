package com.mx.cryptomonitor.transaction.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.transaction.application.dto.response.TransactionDetailsResponse;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;

public interface TransactionQueryUseCase {

  TransactionDetailsResponse getTransactionDetails(UUID userId, UUID transactionId);

  List<TransactionResponse> getTransactionsUser(
      UUID userId, String assetSymbol, String assetType, String transactionType);
}
