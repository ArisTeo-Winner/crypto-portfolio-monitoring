package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.infrastructure.outbound.banxico.BanxicoRateAdapter;

/**
 * Valida BANXICO_TOKEN/BANXICO_BASE_URL contra la API real de Banxico SIE (no un mock). Sufijo IT:
 * no corre con `mvn test`, solo con `mvn verify` — requiere red saliente y credenciales reales, por
 * lo que se omite (no falla) si no hay token en el entorno. Complementa el gate de env vars del
 * Jenkinsfile, que solo detecta token ausente o placeholder, no un token presente pero falso o
 * caducado.
 */
class BanxicoCredentialsIT {

  @Test
  void tokenIsAcceptedByBanxico() {
    String token = System.getenv("BANXICO_TOKEN");
    Assumptions.assumeTrue(
        StringUtils.hasText(token),
        "BANXICO_TOKEN no esta configurado en el entorno, se omite la validacion contra la API"
            + " real");

    String baseUrl = System.getenv("BANXICO_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://www.banxico.org.mx";
    }

    WebClient webClient =
        WebClient.builder().baseUrl(baseUrl).defaultHeader("Bmx-Token", token).build();
    BanxicoRateAdapter adapter = new BanxicoRateAdapter(webClient);

    Map<Integer, BigDecimal> curve;
    try {
      curve = adapter.getCetesCurve();
    } catch (RuntimeException ex) {
      fail(
          "Banxico rechazo la peticion con BANXICO_TOKEN/BANXICO_BASE_URL configurados: token"
              + " falso, caducado o base URL incorrecta. Causa: "
              + ex.getMessage(),
          ex);
      return;
    }

    assertThat(curve)
        .as("Banxico /oportuno respondio sin datos con un token en teoria valido")
        .isNotEmpty();
  }
}
