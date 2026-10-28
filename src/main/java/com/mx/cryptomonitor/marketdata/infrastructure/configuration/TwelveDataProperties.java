package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Propiedades de configuración para Twelve Data.
 *
 * <p>Este proveedor es <b>opcional</b>: si {@code api-key} está vacío, el bean {@code
 * twelveDataStockQuoteProvider} y {@code TwelveDataMarketPriceHistoryAdapter} no se registran (ver
 * {@code @ConditionalOnExpression} en sus configuraciones). Por ello no se usa {@code @NotBlank}:
 * la validación de presencia la hace la condición del bean, no el binding de propiedades.
 */
@Data
@ConfigurationProperties(prefix = "marketdata.twelvedata")
public class TwelveDataProperties {

  private String baseUrl = "https://api.twelvedata.com";

  private String apiKey = "";
}
