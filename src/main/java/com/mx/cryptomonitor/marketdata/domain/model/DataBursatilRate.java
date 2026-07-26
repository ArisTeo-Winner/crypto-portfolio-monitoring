package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tasa de referencia (TIIE, CETES, tasa objetivo, etc.) reportada por DataBursatil /v2/tasas. */
public record DataBursatilRate(BigDecimal rate, LocalDate asOf) {}
