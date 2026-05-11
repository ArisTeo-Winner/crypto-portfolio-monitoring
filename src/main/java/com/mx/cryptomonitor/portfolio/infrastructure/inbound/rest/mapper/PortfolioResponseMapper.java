package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.mapper;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioChartPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioMarkerResponse;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

public final class PortfolioResponseMapper {

  private PortfolioResponseMapper() {}

  public static PortfolioHistoryPointResponse toHistoryPoint(TimeValuePoint point) {
    return new PortfolioHistoryPointResponse(point.time(), point.value());
  }

  public static PortfolioHistoryPointResponse toHistoryPoint(PortfolioChartPointResponse point) {
    return new PortfolioHistoryPointResponse(point.time(), point.value());
  }

  public static PortfolioMarkerResponse toMarker(PortfolioMarker marker) {
    return new PortfolioMarkerResponse(
        marker.time(), marker.position(), marker.color(), marker.shape(), marker.text());
  }
}
