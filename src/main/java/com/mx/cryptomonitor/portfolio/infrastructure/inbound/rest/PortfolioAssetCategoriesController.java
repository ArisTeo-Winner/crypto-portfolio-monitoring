package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.portfolio.application.dto.response.AssetCategoryResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.GetPortfolioAssetCategoriesUseCase;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Devuelve las categorías de activos presentes en el portafolio del usuario.
 *
 * <p>El frontend usa esta respuesta para construir el menú de navegación dinámicamente. El campo
 * {@code key} de cada categoría es el valor exacto que se debe pasar como {@code ?assetType=} en
 * {@code GET /api/v1/me/portfolio/history}.
 */
@Tag(name = "Portfolio", description = "Portafolio del usuario autenticado")
@RestController
@RequestMapping("/api/v1/me/portfolio")
@RequiredArgsConstructor
public class PortfolioAssetCategoriesController {

  private final GetPortfolioAssetCategoriesUseCase getPortfolioAssetCategoriesUseCase;
  private final CurrentUserPort currentUserPort;

  @Operation(
      summary = "Categorías de activos del portafolio",
      description =
          "Devuelve únicamente las categorías para las que el usuario tiene transacciones "
              + "registradas. Cada categoría incluye su key (usar como ?assetType=), label, "
              + "descripción, icono sugerido y los símbolos que el usuario posee. "
              + "La lista está ordenada según la relevancia canónica de la plataforma.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Catálogo calculado exitosamente"),
    @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
    @ApiResponse(responseCode = "403", description = "Acceso denegado")
  })
  @GetMapping("/asset-categories")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<List<AssetCategoryResponse>> getAssetCategories(
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return ResponseEntity.ok(getPortfolioAssetCategoriesUseCase.getCategories(userId));
  }
}
