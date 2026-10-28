package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioMarkerResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.GetAssetHoldingsHistoryUseCase;
import com.mx.cryptomonitor.portfolio.application.port.in.GetAssetMarkersUseCase;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.mapper.PortfolioResponseMapper;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.security.PortfolioHistoryRateLimiter;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me/portfolio")
@RequiredArgsConstructor
public class PortfolioAssetHistoryController {

  private final GetAssetHoldingsHistoryUseCase getAssetHoldingsHistoryUseCase;
  private final GetAssetMarkersUseCase getAssetMarkersUseCase;
  private final CurrentUserPort currentUserPort;
  private final PortfolioHistoryRateLimiter portfolioHistoryRateLimiter;

  @Operation(
      summary = "Obtener historico de valor de holdings por activo",
      description =
          "Reconstruye la cantidad del usuario desde transacciones y la combina con precios historicos compartidos.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Historico calculado exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    array =
                        @ArraySchema(
                            schema =
                                @Schema(implementation = PortfolioHistoryPointResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Rango invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @GetMapping("/assets/{symbol}/history")
  @PreAuthorize("hasRole('USER')")
  public List<PortfolioHistoryPointResponse> getAssetHoldingsHistory(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "6M") String range,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    return getAssetHoldingsHistoryUseCase
        .getAssetHoldingsHistory(userId, symbol, range)
        .series()
        .stream()
        .map(PortfolioResponseMapper::toHistoryPoint)
        .toList();
  }

  @Operation(
      summary = "Obtener marcadores BUY/SELL por activo",
      description =
          "Devuelve marcadores de transacciones del activo autenticado compatibles con Lightweight Charts.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Marcadores calculados exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    array =
                        @ArraySchema(
                            schema = @Schema(implementation = PortfolioMarkerResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Rango invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @GetMapping("/assets/{symbol}/markers")
  @PreAuthorize("hasRole('USER')")
  public List<PortfolioMarkerResponse> getAssetMarkers(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "6M") String range,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    return getAssetMarkersUseCase.getAssetMarkers(userId, symbol, range).stream()
        .map(PortfolioResponseMapper::toMarker)
        .toList();
  }
}
