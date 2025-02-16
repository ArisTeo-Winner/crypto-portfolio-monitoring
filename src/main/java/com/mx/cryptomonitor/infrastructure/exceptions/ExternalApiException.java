package com.mx.cryptomonitor.infrastructure.exceptions;

/**
 * Excepción personalizada para indicar fallas en la comunicación con APIs externas,
 * por ejemplo, al consumir datos de CoinMarketCap o Alpha Vantage.
 */
public class ExternalApiException extends RuntimeException{

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalApiException(String message) {
        super(message);
    }
}
