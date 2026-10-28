package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.model.DataBursatilRate;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.DataBursatilProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilAdapter;

/**
 * Valida DATABURSATIL_TOKEN/DATABURSATIL_BASE_URL contra la API real de DataBursatil (no un mock).
 * Sufijo IT: no corre con `mvn test`, solo con `mvn verify` — requiere red saliente y credenciales
 * reales, por lo que se omite (no falla) si no hay token en el entorno. Un token ausente en CI debe
 * atajarse antes, en el gate de env vars del Jenkinsfile; este test cubre el caso que ese gate no
 * puede ver: token presente pero falso, mal copiado o caducado.
 */
class DataBursatilCredentialsIT {

  @Test
  void tokenIsAcceptedByDataBursatil() {
    String token = System.getenv("DATABURSATIL_TOKEN");
    Assumptions.assumeTrue(
        StringUtils.hasText(token),
        "DATABURSATIL_TOKEN no esta configurado en el entorno, se omite la validacion contra la"
            + " API real");

    String baseUrl = System.getenv("DATABURSATIL_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://api.databursatil.com";
    }

    DataBursatilProperties properties = new DataBursatilProperties(baseUrl, token);
    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
    DataBursatilAdapter adapter = new DataBursatilAdapter(webClient, properties);

    Map<String, DataBursatilRate> rates;
    try {
      rates = adapter.getRates().block(Duration.ofSeconds(10));
    } catch (RuntimeException ex) {
      fail(
          "DataBursatil rechazo la peticion con DATABURSATIL_TOKEN/DATABURSATIL_BASE_URL"
              + " configurados: token falso, caducado o base URL incorrecta. Causa: "
              + ex.getMessage(),
          ex);
      return;
    }

    assertThat(rates)
        .as("DataBursatil /v2/tasas respondio sin datos con un token en teoria valido")
        .isNotEmpty();
  }
}
