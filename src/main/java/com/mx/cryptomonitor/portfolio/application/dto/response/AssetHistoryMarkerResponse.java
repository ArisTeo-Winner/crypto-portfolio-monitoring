package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;

public record AssetHistoryMarkerResponse(
    long time, String type, BigDecimal quantity, BigDecimal price) {}
