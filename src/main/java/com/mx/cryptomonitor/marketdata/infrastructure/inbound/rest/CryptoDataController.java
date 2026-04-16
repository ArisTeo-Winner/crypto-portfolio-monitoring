package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.service.MarketDataReactiveService;
import com.mx.cryptomonitor.marketdata.domain.model.PriceQuote;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.security.CryptoPriceRateLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/crypto")
@RequiredArgsConstructor
public class CryptoDataController {

  private final MarketDataReactiveService marketDataService;
  private final CryptoPriceRateLimiter cryptoPriceRateLimiter;

  @Operation(
      summary = "Obtener el precio actual de una criptomoneda",
      description = "Obtiene el precio actual de una criptomoneda en dolares.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Precio obtenido exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PriceQuote.class))),
        @ApiResponse(responseCode = "404", description = "Criptomoneda no encontrada"),
        @ApiResponse(responseCode = "429", description = "Rate limit excedido"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @GetMapping("/{symbol}/price")
  public Mono<ResponseEntity<PriceQuote>> getCryptoPrice(
      @PathVariable String symbol, HttpServletRequest request) {
    cryptoPriceRateLimiter.validateOrThrow(request);
    return marketDataService
        .getLatestCryptoUsdQuote(symbol)
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());
  }
}
