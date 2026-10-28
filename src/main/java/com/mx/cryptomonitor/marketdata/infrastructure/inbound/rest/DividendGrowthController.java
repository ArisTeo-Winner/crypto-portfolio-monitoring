package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.dto.response.DividendGrowthResponse;
import com.mx.cryptomonitor.marketdata.application.port.in.GetDividendGrowthUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

/**
 * Crecimiento del dividendo de una seguridad (ADR-0014 F5). Público; dato de mercado por seguridad
 * (no por usuario), derivado de los eventos detectados. Cacheable (cambia pocas veces al año).
 */
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class DividendGrowthController {

  private final GetDividendGrowthUseCase getDividendGrowthUseCase;

  @Operation(
      summary = "Crecimiento del dividendo (CAGR 1/3/5/10a) de una seguridad",
      description =
          "DPS anual (años completos, solo recurring, split-adjusted) y CAGR por horizonte."
              + " Público, cacheable.")
  @ApiResponse(
      responseCode = "200",
      description = "Crecimiento calculado (listas/valores vacíos o null si no hay historia)",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = DividendGrowthResponse.class)))
  @GetMapping("/dividends/{symbol}/growth")
  public ResponseEntity<DividendGrowthResponse> dividendGrowth(@PathVariable String symbol) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(getDividendGrowthUseCase.growthFor(symbol));
  }
}
