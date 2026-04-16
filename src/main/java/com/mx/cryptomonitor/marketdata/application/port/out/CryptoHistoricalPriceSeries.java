package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.List;

public record CryptoHistoricalPriceSeries(
    String assetId, String vsCurrency, List<CryptoHistoricalPricePoint> points) {}
