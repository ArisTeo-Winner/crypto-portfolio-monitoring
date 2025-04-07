package com.mx.cryptomonitor.infrastructure.exceptions;

public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) { super(message); }
}