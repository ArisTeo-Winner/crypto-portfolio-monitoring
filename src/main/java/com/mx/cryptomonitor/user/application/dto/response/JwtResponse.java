package com.mx.cryptomonitor.user.application.dto.response;

/**
 * Respuesta de autenticación.
 *
 * <p>Solo expone el {@code accessToken} en el body JSON. El {@code refreshToken} viaja
 * exclusivamente como cookie HttpOnly (Set-Cookie), nunca en el cuerpo — JS no puede leerlo.
 */
public record JwtResponse(String accessToken) {}
