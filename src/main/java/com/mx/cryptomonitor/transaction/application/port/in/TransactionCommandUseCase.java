package com.mx.cryptomonitor.transaction.application.port.in;

import java.util.UUID;

import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.UpdateTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;

public interface TransactionCommandUseCase {

  TransactionResponse registerTransaction(
      UUID userId, TransactionRequest request, String idempotencyKey);

  TransactionResponse registerBuyTransaction(
      UUID userId, BuyTransactionRequest request, String idempotencyKey);

  TransactionResponse registerSellTransaction(
      UUID userId, SellTransactionRequest request, String idempotencyKey);

  TransactionResponse registerTransferTransaction(
      UUID userId, TransferTransactionRequest request, String idempotencyKey);

  TransactionResponse updateTransaction(
      UUID userId, UUID transactionId, UpdateTransactionRequest request, String idempotencyKey);

  void deleteTransactionById(UUID userId, UUID transactionId, String idempotencyKey);
}
