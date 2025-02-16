package com.mx.cryptomonitor.shared.dto;

import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

public interface TransactionMapper {

    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Transaction toEntity(TransactionRequest request);

    TransactionResponse toResponse(Transaction transaction);
}
