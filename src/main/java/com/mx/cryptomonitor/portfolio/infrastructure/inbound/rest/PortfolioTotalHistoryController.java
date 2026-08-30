package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.portfolio.application.port.in.GetPortfolioTotalHistoryUseCase;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioHistoryResult;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.mapper.PortfolioResponseMapper;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.security.PortfolioHistoryRateLimiter;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/me/portfolio")
@RequiredArgsConstructor
public class PortfolioTotalHistoryController {

  private static final String LEGACY_MEDIA_TYPE = "application/vnd.portfolio.v1+json";

  private final GetPortfolioTotalHistoryUseCase getPortfolioTotalHistoryUseCase;
  private final CurrentUserPort currentUserPort;
  private final PortfolioHistoryRateLimiter portfolioHistoryRateLimiter;

  @Operation(
      summary = "Obtener historico agregado del portafolio",
      description =
          "Agrega el valor historico total del portafolio usando transacciones del usuario y"
              + " precios compartidos por activo. Devuelve estructura enriquecida por defecto;"
              + " Accept: application/vnd.portfolio.v1+json para formato legado.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Historico agregado calculado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Rango o assetTypes invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "409", description = "Transacciones inconsistentes")
      })
  @GetMapping("/history")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<Object> getTotalHistory(
      @RequestParam(defaultValue = "1M") String range,
      @Parameter(example = "CRYPTO,STOCK") @RequestParam(required = false) String assetTypes,
      @Parameter(description = "Alias of assetTypes (singular form accepted for compatibility)")
          @RequestParam(required = false)
          String assetType,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);

    // Accept both ?assetType=CRYPTO (singular, used by frontend) and ?assetTypes=CRYPTO,STOCK
    String resolvedAssetTypes = assetTypes != null ? assetTypes : assetType;

    long started = System.currentTimeMillis();
    PortfolioHistoryResult result =
        getPortfolioTotalHistoryUseCase.getTotalHistory(userId, range, resolvedAssetTypes);
    long durationMs = System.currentTimeMillis() - started;

    log.info(
        "portfolio.history.generated userId={} range={} resolution={} points={} durationMs={}",
        userId,
        result.rangeLabel(),
        result.resolution(),
        result.series().size(),
        durationMs);

    String acceptHeader = request.getHeader("Accept");
    if (LEGACY_MEDIA_TYPE.equals(acceptHeader)) {
      return ResponseEntity.ok(PortfolioResponseMapper.toLegacySeries(result));
    }
    return ResponseEntity.ok(PortfolioResponseMapper.toEnrichedResponse(result));
  }
}
