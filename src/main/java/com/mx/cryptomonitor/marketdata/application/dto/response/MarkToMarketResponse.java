package com.mx.cryptomonitor.marketdata.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarkToMarketResponse(
    BigDecimal valorCompra,
    BigDecimal valorHoy,
    BigDecimal valorAlVencimiento,
    BigDecimal mtmPnl,
    BigDecimal mtmPnlPct,
    BigDecimal tasaCompra,
    BigDecimal tasaHoy,
    long diasRestantes,
    Integer plazoSerieUsada,
    LocalDate fechaValuacion,
    boolean vencida) {}
