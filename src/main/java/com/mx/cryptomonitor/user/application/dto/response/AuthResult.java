package com.mx.cryptomonitor.user.application.dto.response;

/**
 * Resultado interno de autenticación: contiene ambos tokens en texto plano.
 *
 * <p>Este record es de uso exclusivo de la capa de aplicación. <b>Nunca</b> debe serializarse
 * directamente como respuesta HTTP. El controlador es responsable de:
 *
 * <ul>
 *   <li>Incluir {@code accessToken} en el body JSON ({@link JwtResponse}).
 *   <li>Escribir {@code rawRefreshToken} como cookie HttpOnly via {@code RefreshTokenCookieHelper}.
 * </ul>
 */
public record AuthResult(String accessToken, String rawRefreshToken) {

  /** Proyecta al DTO público que se envía al cliente (sin refresh token). */
  public JwtResponse toJwtResponse() {
    return new JwtResponse(accessToken);
  }
}
