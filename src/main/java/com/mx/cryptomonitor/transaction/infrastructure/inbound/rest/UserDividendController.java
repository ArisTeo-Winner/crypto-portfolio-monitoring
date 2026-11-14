package com.mx.cryptomonitor.transaction.infrastructure.inbound.rest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.transaction.application.dto.response.UserDividendResponse;
import com.mx.cryptomonitor.transaction.application.port.in.GetUserDividendsUseCase;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;

/**
 * "Mis Dividendos" (ADR-0029): ingresos por dividendo que el usuario ya recibio. Vista propia,
 * separada de las operaciones (/me/transactions ya no lista DIVIDEND). Solo dividendos del usuario
 * autenticado; el scope lo garantiza el backend.
 */
@RestController
@RequestMapping("/api/v1/me/dividends")
@RequiredArgsConstructor
public class UserDividendController {

  private final GetUserDividendsUseCase getUserDividendsUseCase;
  private final CurrentUserPort currentUserPort;

  @Operation(
      summary = "Listar los dividendos recibidos por el usuario autenticado",
      description =
          """
          Devuelve los dividendos YA recibidos (fecha de pago <= hoy) del usuario, con sus montos
          (acciones x dividendo, bruto/retencion/neto). No incluye campos de transaccion ni los
          pagos futuros (esos salen en el proximo pago del resumen de dividendos). Orden: pago mas
          reciente primero.
          """)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Lista de dividendos recibidos",
            content =
                @Content(
                    mediaType = "application/json",
                    array =
                        @ArraySchema(
                            schema = @Schema(implementation = UserDividendResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Token ausente, invalido o expirado")
      })
  @GetMapping
  public ResponseEntity<List<UserDividendResponse>> getUserDividends(
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
        .body(getUserDividendsUseCase.getUserDividends(userId));
  }
}
