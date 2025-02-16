package com.mx.cryptomonitor.infrastructure.exceptions;

/**
 * Excepción personalizada para indicar errores internos en la obtención o
 * procesamiento de datos de mercado (MarketDataService).
 */
public class MarketDataException extends RuntimeException{


    public MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public MarketDataException(String message) {
        super(message);
    }
}
