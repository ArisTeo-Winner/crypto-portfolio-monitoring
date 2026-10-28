package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BmvHistoricalPoint(LocalDate date, BigDecimal closePrice, BigDecimal amountTraded) {}
