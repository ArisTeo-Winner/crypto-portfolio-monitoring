package com.mx.cryptomonitor.transaction.application.port.out;

import java.util.UUID;

import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;

public interface TransactionRegistrationPort {

  TransactionResponse registerTransaction(UUID userId, TransactionRequest request);
}
