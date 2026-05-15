package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.mapper;

import java.util.List;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioChartPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryMeta;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioMarkerResponse;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioHistoryResult;
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

  public static PortfolioHistoryResponse toEnrichedResponse(PortfolioHistoryResult result) {
    List<PortfolioHistoryPointResponse> series =
        result.series().stream().map(PortfolioResponseMapper::toHistoryPoint).toList();
    PortfolioHistoryMeta meta =
        new PortfolioHistoryMeta(
            result.rangeLabel(),
            result.resolution().name(),
            result.from(),
            result.to(),
            "USD",
            series.size());
    return new PortfolioHistoryResponse(meta, series);
  }

  public static List<PortfolioHistoryPointResponse> toLegacySeries(PortfolioHistoryResult result) {
    return result.series().stream().map(PortfolioResponseMapper::toHistoryPoint).toList();
  }

  public static PortfolioMarkerResponse toMarker(PortfolioMarker marker) {
    return new PortfolioMarkerResponse(
        marker.time(), marker.position(), marker.color(), marker.shape(), marker.text());
  }
}
