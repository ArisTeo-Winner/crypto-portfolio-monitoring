package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.util.List;

import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

public record AssetHoldingsHistoryResponse(
    List<TimeValuePoint> series, List<AssetHistoryMarkerResponse> markers) {}
