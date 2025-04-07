package com.mx.cryptomonitor.infrastructure.exceptions;

public class InvalidTokenException  extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}