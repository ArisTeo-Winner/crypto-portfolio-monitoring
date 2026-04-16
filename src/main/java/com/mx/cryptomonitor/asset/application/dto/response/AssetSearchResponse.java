package com.mx.cryptomonitor.asset.application.dto.response;

import java.util.List;

public record AssetSearchResponse(List<AssetOptionResponse> items, int total, String query) {}
