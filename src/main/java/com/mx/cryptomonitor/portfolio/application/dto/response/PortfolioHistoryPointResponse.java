package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;

public record PortfolioHistoryPointResponse(long time, BigDecimal value) {}
