package com.mx.cryptomonitor.transaction.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Ingreso por dividendo que el usuario YA recibio (ADR-0029). Contrato propio de "Mis Dividendos",
 * sin campos de transaccion: representa un pago cobrado (fecha de pago &lt;= hoy) con los montos
 * del usuario.
 *
 * <p>{@code shares} y {@code amountPerShare} pueden ser {@code null} para dividendos acreditados
 * antes de la denormalizacion del dividendo por accion (degradan a "—" en la UI). {@code netAmount}
 * = {@code grossAmount} - {@code taxWithheld}. {@code type} = forma del dividendo: {@code CASH} |
 * {@code STOCK}.
 */
public record UserDividendResponse(
    String symbol,
    String assetName,
    String logoUrl,
    LocalDate exDate,
    OffsetDateTime paymentDate,
    BigDecimal shares,
    BigDecimal amountPerShare,
    BigDecimal grossAmount,
    BigDecimal taxWithheld,
    BigDecimal netAmount,
    String type,
    String currency) {}
