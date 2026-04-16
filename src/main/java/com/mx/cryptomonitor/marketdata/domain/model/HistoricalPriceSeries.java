package com.mx.cryptomonitor.marketdata.domain.model;

import java.util.List;

public record HistoricalPriceSeries(
    String assetId, String vsCurrency, List<HistoricalPricePoint> points, ProviderId providerId) {}
