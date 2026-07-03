package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HybridPriceQuote(
    String symbol, BigDecimal price, String currency, String provider, OffsetDateTime asOf) {}
