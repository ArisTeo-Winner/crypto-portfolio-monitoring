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

import com.mx.cryptomonitor.portfolio.application.port.in.GetPortfolioMarkersUseCase;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.security.PortfolioHistoryRateLimiter;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
public class PortfolioMarkersController {

  private final GetPortfolioMarkersUseCase getPortfolioMarkersUseCase;
  private final CurrentUserPort currentUserPort;
  private final PortfolioHistoryRateLimiter portfolioHistoryRateLimiter;

  @Operation(
      summary = "Obtener marcadores BUY/SELL del historico total del portafolio",
      description =
          "Devuelve marcadores de transacciones del usuario alineados al timeline del chart total.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Marcadores calculados exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = PortfolioMarker.class)))),
        @ApiResponse(responseCode = "400", description = "Rango o assetTypes invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @GetMapping("/{userId}/markers")
  @PreAuthorize("hasRole('USER')")
  public List<PortfolioMarker> getMarkers(
      @PathVariable UUID userId,
      @RequestParam(defaultValue = "30d") String range,
      @Parameter(example = "CRYPTO,STOCK") @RequestParam(required = false) String assetTypes,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID currentUserId = currentUserPort.resolveUserId(authentication);
    if (!currentUserId.equals(userId)) {
      throw new AccessDeniedException("Authenticated user cannot read another user's portfolio");
    }
    return getPortfolioMarkersUseCase.getMarkers(userId, range, assetTypes);
  }
}
