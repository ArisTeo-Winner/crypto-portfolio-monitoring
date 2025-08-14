package com.mx.cryptomonitor.infrastructure.exceptions;

public class NoMarketDataFoundException extends RuntimeException{

    public NoMarketDataFoundException(String symbol, String date) {
        super("No se encontraron datos para el símbolo " + symbol + " en la fecha " + date);
    }

}
