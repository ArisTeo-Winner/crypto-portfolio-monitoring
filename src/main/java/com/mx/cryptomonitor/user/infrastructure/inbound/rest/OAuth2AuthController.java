package com.mx.cryptomonitor.user.infrastructure.inbound.rest;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.user.application.dto.response.JwtResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthController {

  @Operation(
      summary = "Callback OAuth2",
      description = "Procesa el callback despues de autenticacion OAuth2")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Callback procesado correctamente"),
        @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @GetMapping("/callback")
  public JwtResponse handleGoogleLogin(
      HttpServletRequest request,
      HttpServletResponse response,
      OAuth2AuthenticationToken authenticatio) {
    log.info("OAuth2 callback triggered: {}", authenticatio.getName());

    return new JwtResponse("");
  }
}
