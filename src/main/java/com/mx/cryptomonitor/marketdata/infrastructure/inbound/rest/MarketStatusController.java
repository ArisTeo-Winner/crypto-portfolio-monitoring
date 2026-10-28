package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse;
import com.mx.cryptomonitor.marketdata.application.port.in.GetMarketStatusUseCase;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.security.MarketStatusRateLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** Estado en vivo de los mercados (ADR-0012). Público; computado del calendario del backend. */
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketStatusController {

  private final GetMarketStatusUseCase getMarketStatusUseCase;
  private final MarketStatusRateLimiter marketStatusRateLimiter;

  @Operation(
      summary = "Estado actual de los mercados (BMV y NYSE)",
      description =
          "Devuelve fase, horarios y próximo cambio de cada mercado, computado del calendario del"
              + " backend (fuente de verdad). Público, cacheable 30s.")
  @ApiResponse(
      responseCode = "200",
      description = "Estado calculado",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = MarketStatusResponse.class)))
  @GetMapping("/status")
  public ResponseEntity<MarketStatusResponse> status(HttpServletRequest request) {
    marketStatusRateLimiter.validateOrThrow(request);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
        .body(getMarketStatusUseCase.currentStatus());
  }
}
