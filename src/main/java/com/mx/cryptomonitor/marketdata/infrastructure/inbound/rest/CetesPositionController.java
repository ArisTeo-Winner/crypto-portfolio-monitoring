package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.dto.response.MarkToMarketResponse;
import com.mx.cryptomonitor.marketdata.application.service.CetesMarkToMarketService;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/portfolio/cetes")
@RequiredArgsConstructor
public class CetesPositionController {

  private final CetesMarkToMarketService cetesMarkToMarketService;
  private final CurrentUserPort currentUserPort;

  @Operation(
      summary = "Valuacion a mercado (mark-to-market) de una posicion CETES",
      description =
          "Calcula cuanto ganaria o perderia el usuario si liquidara HOY la posicion CETES en vez "
              + "de esperar al vencimiento, usando la curva de tasas CETES vigente de Banxico.")
  @ApiResponse(responseCode = "200", description = "Valuacion calculada correctamente")
  @ApiResponse(responseCode = "400", description = "La transaccion no es un CETES en MXN")
  @ApiResponse(responseCode = "404", description = "Transaccion no encontrada para el usuario")
  @PreAuthorize("hasRole('USER')")
  @GetMapping("/{transactionId}/mark-to-market")
  public ResponseEntity<MarkToMarketResponse> getMarkToMarket(
      @PathVariable UUID transactionId, Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return ResponseEntity.ok(cetesMarkToMarketService.getMarkToMarket(userId, transactionId));
  }
}
