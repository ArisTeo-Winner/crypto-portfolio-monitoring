package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.Instant;

public record HistoricalPricePoint(Instant timestamp, Money price) {}
