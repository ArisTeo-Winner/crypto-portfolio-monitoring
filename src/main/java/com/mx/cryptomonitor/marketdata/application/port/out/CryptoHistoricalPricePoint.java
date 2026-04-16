package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

public record CryptoHistoricalPricePoint(Instant timestamp, BigDecimal priceUsd) {}
