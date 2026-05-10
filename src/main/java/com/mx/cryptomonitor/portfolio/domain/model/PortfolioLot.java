package com.mx.cryptomonitor.portfolio.domain.model;

import java.math.BigDecimal;

public record PortfolioLot(BigDecimal quantity, BigDecimal unitCost) {}
