package com.mx.cryptomonitor.asset.infrastructure.inbound.rest;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.asset.application.dto.response.AssetOptionResponse;
import com.mx.cryptomonitor.asset.application.dto.response.AssetSearchResponse;
import com.mx.cryptomonitor.asset.application.service.AssetSearchService;
import com.mx.cryptomonitor.asset.infrastructure.inbound.rest.security.AssetSearchRateLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

  private final AssetSearchService assetSearchService;
  private final AssetSearchRateLimiter assetSearchRateLimiter;

  @Operation(
      summary = "Buscar activos para el selector de transacciones",
      description =
          "Busca activos por simbolo o nombre para alimentar el modal Select Coin del flujo Add Transaction.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Activos encontrados exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AssetSearchResponse.class))),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos"),
        @ApiResponse(responseCode = "429", description = "Rate limit excedido"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @GetMapping("/popular")
  public Map<String, List<AssetOptionResponse>> getPopular() {
    return Map.of(
        "stocks", assetSearchService.getPopular("stock"),
        "etfs", assetSearchService.getPopular("etf"),
        "cryptos", assetSearchService.getPopular("crypto"));
  }

  @GetMapping("/search")
  public ResponseEntity<AssetSearchResponse> search(
      @RequestParam("q") String query,
      HttpServletRequest request,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assetSearchRateLimiter.validateOrThrow(request);
    return ResponseEntity.ok(assetSearchService.search(query, limit));
  }
}
