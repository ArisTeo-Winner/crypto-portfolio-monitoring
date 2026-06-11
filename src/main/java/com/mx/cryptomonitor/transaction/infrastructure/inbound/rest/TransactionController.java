package com.mx.cryptomonitor.transaction.infrastructure.inbound.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.DividendTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.UpdateTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionDetailsResponse;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionQueryUseCase;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/me/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

  private final TransactionCommandUseCase transactionCommandUseCase;
  private final TransactionQueryUseCase transactionQueryUseCase;
  private final CurrentUserPort currentUserPort;

  /**
   * Endpoint para registrar transacciones. Ejemplo de uso: POST
   * /api/v1/me/transactions?assetSymbol=ETH ...
   *
   * @param symbol Símbolo de activo (no nulo ni vacío)
   * @return ResponseEntity con el precio de cierre o un mensaje de error
   */
  @Operation(
      summary = "Registrar una nueva transacción de inversión para el usuario autenticado",
      description =
          "Registra una transacción (BUY, SELL, TRANSFER) en el portafolio único del usuario. "
              + "El identificador del usuario se obtiene del token JWT, no del cuerpo de la petición.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Transacción registrada exitosamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
							{
							  "assetSymbol": "ETH",
							  "quantity": 1.0,
							  "price": 1000.0,
							  "transactionType": "BUY",
							  "version": 1,
							  "createdAt": "2023-01-01T00:00:00Z",
							  "updatedAt": "2023-01-01T00:00:00Z"
							}
							"""))),
        @ApiResponse(responseCode = "400", description = "Transacción inválida"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PostMapping
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<TransactionResponse> createTransaction(
      @Valid @RequestBody TransactionRequest request,
      @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);

    TransactionResponse response =
        transactionCommandUseCase.registerTransaction(userId, request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/buy")
  @Operation(
      summary = "Registrar compra manual",
      description =
          "Registra una transaccion BUY para el usuario autenticado. "
              + "El backend calcula el valor bruto y el total gastado a partir de quantity, pricePerUnit y fee.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Compra registrada correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                                {
                                  "assetSymbol": "BTC",
                                  "assetType": "CRYPTO",
                                  "transactionType": "BUY",
                                  "quantity": 0.25,
                                  "pricePerUnit": 89208.14,
                                  "totalValue": 22302.04,
                                  "transactionDate": "2026-01-24T17:55:00",
                                  "fee": 0.50,
                                  "notes": "Compra manual",
                                  "createdAt": "2026-01-24T17:55:00",
                                  "updatedAt": "2026-01-24T17:55:00"
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "Payload invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<TransactionResponse> createBuyTransaction(
      @Valid @RequestBody BuyTransactionRequest request,
      @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    TransactionResponse response =
        transactionCommandUseCase.registerBuyTransaction(userId, request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/sell")
  @Operation(
      summary = "Registrar venta manual",
      description =
          "Registra una transaccion SELL para el usuario autenticado. "
              + "El backend calcula el valor bruto y el total recibido a partir de quantity, pricePerUnit y fee.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Venta registrada correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                                {
                                  "assetSymbol": "BTC",
                                  "assetType": "CRYPTO",
                                  "transactionType": "SELL",
                                  "quantity": 0.10,
                                  "pricePerUnit": 70392.39,
                                  "totalValue": 7039.24,
                                  "transactionDate": "2026-03-10T02:11:00",
                                  "fee": 1.25,
                                  "notes": "Venta parcial",
                                  "createdAt": "2026-03-10T02:11:00",
                                  "updatedAt": "2026-03-10T02:11:00"
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "Payload invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<TransactionResponse> createSellTransaction(
      @Valid @RequestBody SellTransactionRequest request,
      @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    TransactionResponse response =
        transactionCommandUseCase.registerSellTransaction(userId, request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/transfer")
  @Operation(
      summary = "Registrar transferencia manual",
      description =
          "Registra una transaccion TRANSFER para el usuario autenticado. "
              + "Solo acepta TRANSFER_IN o TRANSFER_OUT y no requiere pricePerUnit.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Transferencia registrada correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                                {
                                  "assetSymbol": "BTC",
                                  "assetType": "CRYPTO",
                                  "transactionType": "TRANSFER",
                                  "quantity": 0.25,
                                  "pricePerUnit": 0,
                                  "totalValue": 0,
                                  "transactionDate": "2026-03-10T02:11:00",
                                  "fee": 0.0002,
                                  "notes": "Transferencia desde Bitget",
                                  "createdAt": "2026-03-10T02:11:00",
                                  "updatedAt": "2026-03-10T02:11:00"
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "Payload invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<TransactionResponse> createTransferTransaction(
      @Valid @RequestBody TransferTransactionRequest request,
      @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    TransactionResponse response =
        transactionCommandUseCase.registerTransferTransaction(userId, request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/dividend")
  @Operation(
      summary = "Registrar dividendo manual",
      description = "Registra una transaccion DIVIDEND para el usuario autenticado.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Dividendo registrado correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Payload invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<TransactionResponse> createDividendTransaction(
      @Valid @RequestBody DividendTransactionRequest request,
      @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    TransactionResponse response =
        transactionCommandUseCase.registerDividendTransaction(userId, request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(
      summary = "Listar transacciones del usuario autenticado",
      description =
          """
        Devuelve las transacciones del usuario autenticado.

        Todos los filtros son opcionales:
        - Sin filtros: devuelve todas las transacciones del usuario.
        - Con un filtro: devuelve las transacciones que cumplan ese criterio.
        - Con múltiples filtros: devuelve las transacciones que cumplan todos los criterios enviados.
        """)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Lista de transacciones obtenida correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    array =
                        @ArraySchema(
                            schema = @Schema(implementation = TransactionResponse.class)))),
        @ApiResponse(
            responseCode = "400",
            description = "Parámetros de consulta inválidos",
            content = @Content(mediaType = "application/json")),
        @ApiResponse(
            responseCode = "401",
            description = "Token ausente, inválido o expirado",
            content = @Content(mediaType = "application/json")),
        @ApiResponse(
            responseCode = "500",
            description = "Error interno inesperado",
            content = @Content(mediaType = "application/json"))
      })
  @GetMapping
  public ResponseEntity<List<TransactionResponse>> getUserTransactions(
      @Parameter(
              description = "Símbolo del activo a filtrar. Opcional.",
              required = false,
              example = "HYPE")
          @RequestParam(value = "assetSymbol", required = false)
          String assetSymbol,
      @Parameter(
              description = "Tipo de activo a filtrar. Opcional.",
              required = false,
              example = "CRYPTO")
          @RequestParam(value = "assetType", required = false)
          String assetType,
      @Parameter(
              description = "Tipo de transaccion a filtrar. Opcional.",
              required = false,
              example = "BUY")
          @RequestParam(value = "transactionType", required = false)
          String transactionType,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);

    List<TransactionResponse> transactions =
        transactionQueryUseCase.getTransactionsUser(
            userId, assetSymbol, assetType, transactionType);
    return ResponseEntity.ok(transactions);
  }

  @GetMapping("/details/{transactionId}")
  @Operation(
      summary = "Obtener detalle de transaccion",
      description =
          "Retorna el detalle enriquecido de una transaccion del usuario autenticado, "
              + "incluyendo grossAmount, netAmount, amountLabel y transferType cuando aplica.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Detalle obtenido correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransactionDetailsResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                                {
                                  "id": "3dc07e3d-3a4b-41fb-b5c4-3f6d43c1b676",
                                  "assetSymbol": "BTC",
                                  "assetType": "CRYPTO",
                                  "transactionType": "BUY",
                                  "transferType": null,
                                  "transactionDate": "2026-01-24T17:55:00",
                                  "quantity": 0.25,
                                  "pricePerUnit": 89208.14,
                                  "grossAmount": 22302.04,
                                  "fee": 0.50,
                                  "feeCurrency": "USD",
                                  "netAmount": 22302.54,
                                  "amountLabel": "Total Spent",
                                  "notes": "Compra manual",
                                  "source": "MANUAL",
                                  "exchange": null,
                                  "status": "COMPLETED"
                                }
                                """))),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "404", description = "Transaccion no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<TransactionDetailsResponse> getTransactionDetails(
      @PathVariable UUID transactionId, Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return ResponseEntity.ok(transactionQueryUseCase.getTransactionDetails(userId, transactionId));
  }

  @PutMapping("/{transactionId}")
  @PreAuthorize("hasRole('USER')")
  @Operation(
      summary = "Editar transaccion del usuario autenticado",
      description =
          "Actualiza una transaccion existente del usuario autenticado y reconcilia inmediatamente la proyeccion del portafolio.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Transaccion actualizada correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Payload invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "404", description = "Transaccion no encontrada")
      })
  public ResponseEntity<TransactionResponse> updateTransaction(
      @PathVariable UUID transactionId,
      @Valid @RequestBody UpdateTransactionRequest request,
      @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return ResponseEntity.ok(
        transactionCommandUseCase.updateTransaction(
            userId, transactionId, request, idempotencyKey));
  }

  @DeleteMapping("/{transactionId}")
  @PreAuthorize("hasRole('USER')")
  @Operation(
      summary = "Eliminar transaccion del usuario autenticado",
      description =
          "Elimina una transaccion del usuario autenticado y reconcilia inmediatamente la proyeccion del portafolio.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Transaccion eliminada correctamente"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado"),
        @ApiResponse(responseCode = "404", description = "Transaccion no encontrada")
      })
  public ResponseEntity<Void> deleteTransaction(
      @PathVariable UUID transactionId,
      @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    transactionCommandUseCase.deleteTransactionById(userId, transactionId, idempotencyKey);
    return ResponseEntity.noContent().build();
  }
}
