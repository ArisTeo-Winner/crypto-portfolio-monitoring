package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.portfolio.application.dto.response.AssetHoldingsHistoryResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.GetAssetMarkersUseCase;
import com.mx.cryptomonitor.portfolio.application.port.in.GetAssetHoldingsHistoryUseCase;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;
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
@RequestMapping("/api/v1")
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
                    schema = @Schema(implementation = AssetHoldingsHistoryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Rango invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @GetMapping("/me/portfolio/{userId}/assets/{symbol}/history")
  @PreAuthorize("hasRole('USER')")
  public AssetHoldingsHistoryResponse getAssetHoldingsHistory(
      @PathVariable UUID userId,
      @PathVariable String symbol,
      @RequestParam(defaultValue = "180d") String range,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID currentUserId = currentUserPort.resolveUserId(authentication);
    if (!currentUserId.equals(userId)) {
      throw new AccessDeniedException("Authenticated user cannot read another user's portfolio");
    }
    return getAssetHoldingsHistoryUseCase.getAssetHoldingsHistory(userId, symbol, range);
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
                    array = @ArraySchema(schema = @Schema(implementation = PortfolioMarker.class)))),
        @ApiResponse(responseCode = "400", description = "Rango invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @GetMapping("/portfolio/assets/{symbol}/markers")
  @PreAuthorize("hasRole('USER')")
  public List<PortfolioMarker> getAssetMarkers(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "180d") String range,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    return getAssetMarkersUseCase.getAssetMarkers(userId, symbol, range);
  }
}
