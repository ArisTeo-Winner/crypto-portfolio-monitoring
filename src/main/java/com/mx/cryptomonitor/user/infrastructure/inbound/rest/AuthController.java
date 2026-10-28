package com.mx.cryptomonitor.user.infrastructure.inbound.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.user.application.dto.request.LoginRequest;
import com.mx.cryptomonitor.user.application.dto.response.AuthResult;
import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;
import com.mx.cryptomonitor.user.application.service.AuthService;
import com.mx.cryptomonitor.user.infrastructure.security.LoginRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.security.RefreshTokenCookieHelper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final Logger logger = LoggerFactory.getLogger(AuthController.class);

  @Autowired private AuthService authService;
  @Autowired private LoginRateLimiter loginRateLimiter;
  @Autowired private RefreshTokenCookieHelper cookieHelper;

  @Operation(
      summary = "Iniciar sesión",
      description = "Inicia sesión con las credenciales proporcionadas.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Inicio de sesión exitoso",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = JwtResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
           {
              "accessToken": "new-access-token",
              "refreshToken": "new-refresh-token"
           }
          """))),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos de login"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PostMapping("/login")
  public ResponseEntity<JwtResponse> login(
      @Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {

    loginRateLimiter.validateOrThrow(request);
    AuthResult result = authService.login(loginRequest, request);

    // El refresh token viaja como HttpOnly cookie — JS nunca puede leerlo.
    // El access token va en el body JSON — el frontend lo guarda en memoria (no localStorage).
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(
            cookieHelper.headerName(), cookieHelper.buildSetCookieHeader(result.rawRefreshToken()))
        .body(result.toJwtResponse());
  }

  /*
   * Endpoint - Cierra la sesión del usuario actual. POST /api/v1/auth/logout
   **/
  @Operation(
      summary = "Cerrar sesion",
      description = "Invalida el refresh token actual y cierra la sesion")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Sesion cerrada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Refresh token invalido"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PostMapping("/logout")
  public ResponseEntity<String> logout(HttpServletRequest request) {

    // El refresh token llega en la cookie HttpOnly, no en un header legible por JS.
    String refreshToken = cookieHelper.extractFromRequest(request);
    if (refreshToken == null || refreshToken.isBlank()) {
      return ResponseEntity.badRequest().body("Cookie de sesión no encontrada");
    }
    authService.logout(refreshToken);

    // Borrar la cookie en el cliente (maxAge=0).
    return ResponseEntity.ok()
        .header(cookieHelper.headerName(), cookieHelper.buildClearCookieHeader())
        .body("Sesión cerrada exitosamente");
  }

  @Operation(
      summary = "Dashboard OAuth2",
      description = "Retorna un mensaje de bienvenida para el usuario autenticado por OAuth2")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Acceso al dashboard exitoso"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado")
      })
  @GetMapping("/dashboard")
  public String dashboard(@AuthenticationPrincipal OAuth2User principal) {
    return "Bienvenido, " + principal.getAttribute("name");
  }
}
