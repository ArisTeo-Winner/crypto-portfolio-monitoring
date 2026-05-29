package com.mx.cryptomonitor.user.infrastructure.inbound.rest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
import com.mx.cryptomonitor.user.application.service.TokenService;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;
import com.mx.cryptomonitor.user.infrastructure.security.RefreshTokenCookieHelper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tokens")
@RequiredArgsConstructor
public class TokenController {

  private final TokenService tokenService;
  private final RefreshTokenCookieHelper cookieHelper;

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
  public ResponseEntity<Void> revokeRefreshToken(HttpServletRequest request) {
    String refreshToken = cookieHelper.extractFromRequest(request);
    if (refreshToken == null || refreshToken.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      tokenService.revokeRefreshToken(refreshToken);
      return ResponseEntity.noContent()
          .header(cookieHelper.headerName(), cookieHelper.buildClearCookieHeader())
          .build();
    } catch (SecurityException ex) {
      throw new InvalidTokenException("Refresh token inválido");
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
  public ResponseEntity<JwtResponse> refreshToken(HttpServletRequest request) {

    // El refresh token llega automáticamente en la cookie HttpOnly — el browser lo adjunta
    // sin ninguna intervención de JS.
    String refreshToken = cookieHelper.extractFromRequest(request);
    if (refreshToken == null || refreshToken.isBlank()) {
      // Cookie ausente: sesión cerrada o nunca iniciada — semánticamente no autenticado.
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    AuthResult result = tokenService.refreshToken(refreshToken, request);

    // Rotación de cookie: el token anterior queda revocado en Redis, el nuevo se escribe aquí.
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(
            cookieHelper.headerName(), cookieHelper.buildSetCookieHeader(result.rawRefreshToken()))
        .body(result.toJwtResponse());
  }
}
