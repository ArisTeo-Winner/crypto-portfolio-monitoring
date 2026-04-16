package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioEntryResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse;
import com.mx.cryptomonitor.portfolio.application.mapper.PortfolioEntryMapper;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioEntryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioQueryPort;
import com.mx.cryptomonitor.portfolio.domain.exception.PortfolioEntryNotFoundException;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

  private final PortfolioQueryPort portfolioQueryPort;
  private final PortfolioEntryPort portfolioEntryPort;
  private final PortfolioEntryMapper portfolioEntryMapper;
  private final CurrentUserPort currentUserPort;

  @Operation(
      summary = "Listar holdings del usuario autenticado",
      description = "Obtiene las posiciones actuales del portafolio del usuario autenticado.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Portafolio obtenido exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    array =
                        @ArraySchema(
                            schema = @Schema(implementation = PortfolioEntryResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @GetMapping
  @PreAuthorize("hasRole('USER')")
  public List<PortfolioEntryResponse> getCurrentUserPortfolio(Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return portfolioQueryPort.getPortfolioEntriesByUser(userId).stream()
        .map(portfolioEntryMapper::toResponse)
        .toList();
  }

  @Operation(
      summary = "Obtener holding por activo",
      description = "Obtiene la posicion actual del usuario autenticado para un activo especifico.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Holding obtenido exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PortfolioEntryResponse.class))),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "404", description = "Activo no encontrado en el portafolio")
      })
  @GetMapping("/{assetSymbol}")
  @PreAuthorize("hasRole('USER')")
  public PortfolioEntryResponse getCurrentUserPortfolioEntry(
      @PathVariable String assetSymbol, Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return portfolioQueryPort
        .getPortfolioEntryByUserAndSymbol(userId, assetSymbol.toUpperCase())
        .map(portfolioEntryMapper::toResponse)
        .orElseThrow(
            () ->
                new PortfolioEntryNotFoundException(
                    "No portfolio entry found for asset: " + assetSymbol.toUpperCase()));
  }

  @Operation(
      summary = "Obtener performance historica del portfolio",
      description =
          "Calcula la serie temporal de holdings del usuario autenticado usando average cost method.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Performance historica obtenida exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PortfolioHoldingsPerformanceResponse.class))),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @GetMapping("/{portfolioId}/holdings-performance")
  @PreAuthorize("hasRole('USER')")
  public PortfolioHoldingsPerformanceResponse getCurrentUserHoldingsPerformance(
      @PathVariable String portfolioId,
      @RequestParam(defaultValue = "ALL") String period,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return portfolioQueryPort.getHoldingsPerformanceByPortfolioId(userId, portfolioId, period);
  }

  @Operation(
      summary = "Reconciliar portafolio del usuario autenticado",
      description =
          "Reconstruye la proyeccion de holdings del usuario autenticado tomando transaction como fuente de verdad.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Portafolio reconciliado exitosamente"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @PostMapping("/reconcile")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<Void> reconcileCurrentUserPortfolio(Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    portfolioEntryPort.reconcilePortfolio(userId);
    return ResponseEntity.noContent().build();
  }
}
