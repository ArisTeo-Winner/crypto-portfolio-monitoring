package com.mx.cryptomonitor.user.application.port.in;

import java.util.UUID;

import org.springframework.security.core.Authentication;

public interface CurrentUserPort {
  UUID resolveUserId(Authentication authentication);

  /** Moneda de presentacion preferida del usuario autenticado (ISO-4217); "USD" por defecto. */
  String resolvePreferredCurrency(Authentication authentication);
}
