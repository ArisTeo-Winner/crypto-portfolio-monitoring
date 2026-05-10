package com.mx.cryptomonitor.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioMarker(
    Instant time, String type, String assetSymbol, AssetType assetType, BigDecimal quantity, BigDecimal price) {}
