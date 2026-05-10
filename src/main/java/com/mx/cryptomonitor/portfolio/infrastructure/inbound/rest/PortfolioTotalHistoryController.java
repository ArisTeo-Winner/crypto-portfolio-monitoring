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

import com.mx.cryptomonitor.portfolio.application.port.in.GetPortfolioTotalHistoryUseCase;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;
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
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioTotalHistoryController {

  private final GetPortfolioTotalHistoryUseCase getPortfolioTotalHistoryUseCase;
  private final CurrentUserPort currentUserPort;
  private final PortfolioHistoryRateLimiter portfolioHistoryRateLimiter;

  @Operation(
      summary = "Obtener historico agregado del portafolio",
      description =
          "Agrega el valor historico total del portafolio usando transacciones del usuario y precios compartidos por activo.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Historico agregado calculado exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = TimeValuePoint.class)))),
        @ApiResponse(responseCode = "400", description = "Rango o assetTypes invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "409", description = "Transacciones inconsistentes")
      })
  @GetMapping("/{userId}/history")
  @PreAuthorize("hasRole('USER')")
  public List<TimeValuePoint> getTotalHistory(
      @PathVariable UUID userId,
      @RequestParam(defaultValue = "30d") String range,
      @Parameter(example = "CRYPTO,STOCK") @RequestParam(required = false) String assetTypes,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID currentUserId = currentUserPort.resolveUserId(authentication);
    if (!currentUserId.equals(userId)) {
      throw new IllegalArgumentException("Authenticated user cannot read another user's portfolio");
    }
    return getPortfolioTotalHistoryUseCase.getTotalHistory(userId, range, assetTypes);
  }
}
