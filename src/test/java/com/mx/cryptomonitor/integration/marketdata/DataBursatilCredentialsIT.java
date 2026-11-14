package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.domain.model.DataBursatilRate;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.DataBursatilProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilDividendAdapter;

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

  @Test
  void dividendsGateReturnsBmvMxnSeries() {
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
    DataBursatilDividendAdapter adapter = new DataBursatilDividendAdapter(webClient, properties);

    List<DividendEventData> dividends = adapter.fetchDividends("BIMBOA");

    assertThat(dividends)
        .as(
            "DataBursatil /v2/emisoras no devolvio dividendos de BIMBOA: revisar mapeo letra/serie,"
                + " creditos del token o el contrato del endpoint (el adapter traga el error y"
                + " devuelve vacio).")
        .isNotEmpty();
    assertThat(dividends)
        .as("DataBursatil dividendos de BIMBOA deben venir en MXN nativo")
        .allSatisfy(d -> assertThat(d.currency()).isEqualTo("MXN"));
    assertThat(dividends)
        .as("DataBursatil dividendos de BIMBOA sin monto positivo o sin ex-date")
        .anySatisfy(
            d -> {
              assertThat(d.cashAmount().signum()).isPositive();
              assertThat(d.exDividendDate()).isNotNull();
            });
  }
}
