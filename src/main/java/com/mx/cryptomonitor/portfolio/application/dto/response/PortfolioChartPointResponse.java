package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;

public record PortfolioChartPointResponse(long time, BigDecimal value) {}
