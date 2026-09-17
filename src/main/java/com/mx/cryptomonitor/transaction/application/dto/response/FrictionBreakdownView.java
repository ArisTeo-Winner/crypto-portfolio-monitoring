package com.mx.cryptomonitor.transaction.application.dto.response;

import java.math.BigDecimal;

import com.mx.cryptomonitor.transaction.domain.friction.ReviewStatus;

/**
 * Desglose de friccion de corretaje para auditoria del usuario en la UI. Los campos
 * gross/total/net/ adjusted son derivados y siempre estan presentes;
 * comision/IVA/otherFees/reviewStatus solo cuando la transaccion se importo con desglose de broker
 * (NULL en altas manuales).
 */
public record FrictionBreakdownView(
    BigDecimal grossAmount,
    BigDecimal brokerCommission,
    BigDecimal brokerIva,
    BigDecimal otherFees,
    BigDecimal totalFrictionCost,
    BigDecimal finalNetCost,
    BigDecimal adjustedUnitPrice,
    BigDecimal perUnitFriction,
    ReviewStatus reviewStatus) {}
