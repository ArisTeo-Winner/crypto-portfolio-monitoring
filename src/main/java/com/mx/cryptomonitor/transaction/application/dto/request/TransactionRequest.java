package com.mx.cryptomonitor.transaction.application.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.mx.cryptomonitor.transaction.domain.model.AssetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransactionRequest(
    @NotBlank(message = "El simbolo del activo no puede estar vacio") String assetSymbol,
    @NotNull(message = "El tipo de activo no puede estar vacio") AssetType assetType,
    @NotBlank(message = "El tipo de transaccion no puede estar vacio") String transactionType,
    @Positive(message = "La cantidad debe ser mayor que cero") BigDecimal quantity,
    @NotNull(message = "El precio por unidad no puede ser nulo") BigDecimal pricePerUnit,
    @NotNull(message = "El valor total no puede ser nulo") BigDecimal totalValue,
    OffsetDateTime transactionDate,
    BigDecimal fee,
    String notes,
    String transferType) {

  public TransactionRequest(
      String assetSymbol,
      AssetType assetType,
      String transactionType,
      BigDecimal quantity,
      BigDecimal pricePerUnit,
      BigDecimal totalValue,
      OffsetDateTime transactionDate,
      BigDecimal fee,
      String notes) {
    this(
        assetSymbol,
        assetType,
        transactionType,
        quantity,
        pricePerUnit,
        totalValue,
        transactionDate,
        fee,
        notes,
        null);
  }
}
