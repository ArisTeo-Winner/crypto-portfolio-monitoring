package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import org.springframework.web.reactive.function.client.ClientResponse;

import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;

import reactor.core.publisher.Mono;

/**
 * Mapeo terminal de respuestas de error no contempladas por los {@code onStatus} específicos de un
 * adapter de precios (ADR-0021).
 *
 * <p>Cada adapter cubre a mano los casos que entiende (400/404 símbolo inexistente, 401/403 auth,
 * 429 rate limit, 5xx servidor), pero un status de error FUERA de esa lista —sobre todo {@code 451
 * Unavailable For Legal Reasons} o {@code 403 Forbidden} de un geobloqueo de compliance US, también
 * 418, 409…— se escapaba como {@code WebClientResponseException} crudo y la cadena de valuación
 * terminaba en HTTP 500. Registrando {@link #recoverable} como ÚLTIMO {@code onStatus(}{@code
 * HttpStatusCode::isError}{@code , ...)} del adapter, ese resto se traduce a una excepción
 * RECUPERABLE ({@link MarketDataServerException}) y {@code CachedMarketPriceHistoryAdapter} cae al
 * siguiente proveedor en vez de romper.
 *
 * <p>WebClient evalúa los predicados de {@code onStatus} en orden de registro y aplica el primero
 * que machea, así que este catch-all NO pisa a los handlers específicos mientras se registre al
 * final; {@code isError()} cubre 4xx y 5xx, pero los 5xx ya los toma el handler previo.
 */
final class MarketDataResponseErrors {

  private static final int MAX_BODY_SNIPPET = 200;

  private MarketDataResponseErrors() {}

  static Mono<? extends Throwable> recoverable(String provider, ClientResponse response) {
    int status = response.statusCode().value();
    return response
        .bodyToMono(String.class)
        .defaultIfEmpty("")
        .map(
            body ->
                new MarketDataServerException(
                    provider + " non-2xx HTTP " + status + snippet(body)));
  }

  private static String snippet(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    String trimmed = body.strip();
    return ": "
        + (trimmed.length() > MAX_BODY_SNIPPET ? trimmed.substring(0, MAX_BODY_SNIPPET) : trimmed);
  }
}
