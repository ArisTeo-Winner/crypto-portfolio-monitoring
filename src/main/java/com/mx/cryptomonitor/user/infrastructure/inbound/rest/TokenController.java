package com.mx.cryptomonitor.user.infrastructure.inbound.rest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
import com.mx.cryptomonitor.user.application.service.TokenService;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tokens")
@RequiredArgsConstructor
public class TokenController {

  private final TokenService tokenService;

  @Operation(
      summary = "Revocar refresh token",
      description = "Revoca un refresh token y lo deja invÃ¡lido.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Token revocado correctamente"),
        @ApiResponse(responseCode = "400", description = "Header invÃ¡lido"),
        @ApiResponse(responseCode = "401", description = "Token invÃ¡lido")
      })
  @PostMapping("/revoke")
  public ResponseEntity<Void> revokeRefreshToken(
      @RequestHeader("X-Refresh-Token") String refreshToken) {
    try {
      tokenService.revokeRefreshToken(refreshToken);
      return ResponseEntity.noContent().build();
    } catch (SecurityException ex) {
      throw new InvalidTokenException("Refresh token invÃ¡lido");
    }
  }

  /*
   * Endpoint - Refresh Token para obtener un nuevo Access Toke POST
   * /api/v1/users/refresh
   */
  @Operation(
      summary = "Refrescar tokens",
      description = "Genera un nuevo access token usando un refresh token valido")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Token refrescado correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = JwtResponse.class))),
        @ApiResponse(responseCode = "400", description = "Refresh token invalido"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PostMapping("/refresh")
  public ResponseEntity<JwtResponse> refreshToken(
      @RequestHeader("X-Refresh-Token") String refreshToken) {

    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new IllegalArgumentException("Encabezado X-Refresh-Token no proporcionado o vacÃ­o");
    }

    JwtResponse refreshJwt = tokenService.refreshToken(refreshToken);

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(refreshJwt);
  }
}
