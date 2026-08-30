package com.mx.cryptomonitor.transaction.application.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Alta de una operacion de acciones importada desde un broker, con el desglose crudo de friccion
 * (comision, fees) tal como lo reporta el comprobante. El calculo del costo neto y la validacion se
 * hacen dentro del modulo transaction; este DTO solo transporta los datos crudos desde el import.
 */
public record ImportedStockTransactionRequest(
    String assetSymbol,
    String assetName,
    boolean buy,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    OffsetDateTime transactionDate,
    String notes,
    String exchange,
    String broker,
    String currency,
    BigDecimal principalAmount,
    BigDecimal commission,
    BigDecimal transactionFee,
    BigDecimal otherFees,
    BigDecimal netAmount,
    ImportedBrokerKind brokerKind) {}
