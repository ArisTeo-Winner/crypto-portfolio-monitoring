package com.mx.cryptomonitor.application.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;


@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    @Mapping(target = "transactionId", expression = "java(java.util.UUID.randomUUID())") // 🔥 Solución
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    Transaction toEntity(TransactionRequest request);

    @Mapping(source = "user.id", target = "userId")
    TransactionResponse toResponse(Transaction transaction);
}

