package com.mx.cryptomonitor.asset.application.dto.response;

public record AssetOptionResponse(
    String assetId,
    String symbol,
    String name,
    String assetType,
    String logoUrl,
    boolean supportedForTransactions) {}
