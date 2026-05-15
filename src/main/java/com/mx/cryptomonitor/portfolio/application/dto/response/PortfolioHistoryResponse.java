package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.util.List;

public record PortfolioHistoryResponse(
    PortfolioHistoryMeta meta, List<PortfolioHistoryPointResponse> series) {}
