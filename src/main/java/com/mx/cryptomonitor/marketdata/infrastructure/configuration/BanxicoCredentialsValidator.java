package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Fail-fast en produccion sobre la credencial de Banxico SIE. Como {@code
 * banxico.token=${BANXICO_TOKEN:}} tiene default vacio y {@link BanxicoWebClientConfig} solo agrega
 * el header {@code Bmx-Token} cuando hay texto, un token ausente o placeholder haria que toda
 * peticion a Banxico falle con 401 en runtime, de forma silenciosa. En perfil {@code prod} se
 * prefiere detener el arranque. Ver ADR-0001.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class BanxicoCredentialsValidator {

  private final BanxicoProperties banxicoProperties;

  @PostConstruct
  public void validateBanxicoToken() {
    String token = banxicoProperties.token();
    if (!StringUtils.hasText(token) || token.contains("__SET_")) {
      throw new IllegalStateException(
          "BANXICO_TOKEN no esta configurado (o es un placeholder). El adaptador de Banxico SIE"
              + " (curva CETES / tasa de referencia) fallaria con 401 en produccion. Configure la"
              + " variable de entorno BANXICO_TOKEN con el token real.");
    }
  }
}
