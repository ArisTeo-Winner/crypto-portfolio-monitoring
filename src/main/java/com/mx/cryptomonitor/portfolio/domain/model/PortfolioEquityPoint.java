package com.mx.cryptomonitor.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioEquityPoint(Instant time, BigDecimal value) {}
