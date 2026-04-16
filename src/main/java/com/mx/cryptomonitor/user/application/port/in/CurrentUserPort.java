package com.mx.cryptomonitor.user.application.port.in;

import java.util.UUID;

import org.springframework.security.core.Authentication;

public interface CurrentUserPort {
  UUID resolveUserId(Authentication authentication);
}
