package com.mx.cryptomonitor.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record QuantityTimelinePoint(Instant time, BigDecimal quantity) {}
