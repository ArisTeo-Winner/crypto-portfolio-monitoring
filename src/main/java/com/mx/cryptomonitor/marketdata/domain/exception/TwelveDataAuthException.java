package com.mx.cryptomonitor.marketdata.domain.exception;

/**
 * Se lanza cuando Twelve Data rechaza la solicitud por credenciales inválidas o expiradas (HTTP 401
 * / 403, o body con code 401/403).
 *
 * <p>Extiende {@link TwelveDataException} (que a su vez extiende {@link
 * ExternalProviderUpstreamException}) para que el {@code StockQuoteOrchestrator} realice el
 * fallback hacia el siguiente proveedor configurado, evitando interrumpir el servicio mientras se
 * renueva la API key.
 */
public class TwelveDataAuthException extends TwelveDataException {

  public TwelveDataAuthException(String message) {
    super(message);
  }
}
