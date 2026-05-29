package com.mx.cryptomonitor.transaction.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
  TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

  @Mapping(target = "transactionId", ignore = true)
  @Mapping(
      target = "createdAt",
      expression = "java(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))")
  @Mapping(
      target = "updatedAt",
      expression = "java(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))")
  Transaction toEntity(TransactionRequest request);

  TransactionResponse toResponse(Transaction transaction);
}
