package com.mx.cryptomonitor.infrastructure.exceptions;

/**
 * Excepción personalizada para indicar que un usuario no fue encontrado en la base de datos.
 */
public class UserNotFoundException extends RuntimeException{
    /**
     * Constructor con mensaje personalizado.
     * 
     * @param message Mensaje de error.
     */
    public UserNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructor con mensaje personalizado y causa.
     * 
     * @param message Mensaje de error.
     * @param cause   Causa de la excepción.
     */
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
