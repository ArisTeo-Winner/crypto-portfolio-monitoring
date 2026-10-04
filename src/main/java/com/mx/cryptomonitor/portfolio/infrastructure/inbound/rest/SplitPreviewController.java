package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.portfolio.application.dto.request.SplitPreviewRequest;
import com.mx.cryptomonitor.portfolio.application.dto.response.SplitPreviewResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.GetSplitPreviewUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me/portfolio")
@RequiredArgsConstructor
public class SplitPreviewController {

  private final GetSplitPreviewUseCase getSplitPreviewUseCase;

  @Operation(
      summary = "Preview de ajuste por split para una operacion en captura",
      description =
          "Dado fecha/ticker/cantidad/precio, indica si hubo un split entre esa fecha y hoy y como"
              + " se veria la operacion ya ajustada. No persiste nada: la transaccion se guarda cruda"
              + " y la proyeccion aplica el factor en lectura (ADR-0011).")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Preview calculado",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SplitPreviewResponse.class))),
        @ApiResponse(responseCode = "400", description = "Request invalido"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "403", description = "Usuario no autorizado")
      })
  @PostMapping("/split-preview")
  @PreAuthorize("hasRole('USER')")
  public SplitPreviewResponse previewSplit(@Valid @RequestBody SplitPreviewRequest request) {
    return getSplitPreviewUseCase.preview(request);
  }
}
