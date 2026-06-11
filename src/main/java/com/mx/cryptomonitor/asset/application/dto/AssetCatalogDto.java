package com.mx.cryptomonitor.asset.application.dto;

public record AssetCatalogDto(
    String symbol,
    String name,
    String assetType,
    String logoUrl,
    String exchange,
    String currency,
    Long marketCap) {}
