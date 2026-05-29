package com.mx.cryptomonitor.user.infrastructure.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Centraliza la creación y lectura de la cookie HttpOnly que transporta el refresh token.
 *
 * <p>Atributos de seguridad aplicados:
 *
 * <ul>
 *   <li><b>HttpOnly</b> — JS no puede leer ni modificar la cookie (protección XSS).
 *   <li><b>Secure</b> — solo se envía por HTTPS (configurable para desactivar en local).
 *   <li><b>SameSite=Strict</b> — no se envía en requests cross-site (protección CSRF).
 *   <li><b>Path=/api/v1/</b> — el browser solo adjunta la cookie a rutas de API, no a páginas.
 * </ul>
 */
@Component
public class RefreshTokenCookieHelper {

  public static final String COOKIE_NAME = "refresh_token";
  private static final String COOKIE_PATH = "/api/v1/";

  @Value("${app.refresh-token.ttl-days:30}")
  private long refreshTokenTtlDays;

  @Value("${app.cookie.secure:true}")
  private boolean secureCookie;

  /** Crea el header {@code Set-Cookie} que escribe el refresh token como cookie HttpOnly. */
  public String buildSetCookieHeader(String refreshTokenValue) {
    return ResponseCookie.from(COOKIE_NAME, refreshTokenValue)
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(Duration.ofDays(refreshTokenTtlDays))
        .build()
        .toString();
  }

  /** Crea el header {@code Set-Cookie} que elimina la cookie (maxAge=0). */
  public String buildClearCookieHeader() {
    return ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(Duration.ZERO)
        .build()
        .toString();
  }

  /**
   * Extrae el valor del refresh token de las cookies del request. Devuelve {@code null} si la
   * cookie no está presente.
   */
  public String extractFromRequest(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  /** Nombre del header HTTP usado para escribir/borrar la cookie. */
  public String headerName() {
    return HttpHeaders.SET_COOKIE;
  }
}
