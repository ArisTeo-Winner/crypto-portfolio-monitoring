package com.mx.cryptomonitor.user.infrastructure.inbound.rest;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/oauth2")
public class OAuth2AliasController {

  @Operation(
      summary = "Iniciar login con Google",
      description = "Redirige al endpoint OAuth2 de Spring Security para Google")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "302", description = "Redireccion a proveedor OAuth2"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @GetMapping("/authorize/google")
  public void startGoogle(HttpServletResponse response) throws IOException {
    response.sendRedirect("/oauth2/authorization/google");
  }

  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal UserDetails me, HttpServletRequest req) {

    return null;
  }
}
