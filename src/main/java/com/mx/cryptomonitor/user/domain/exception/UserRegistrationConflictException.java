package com.mx.cryptomonitor.user.domain.exception;

public class UserRegistrationConflictException extends RuntimeException {

  public UserRegistrationConflictException(String message) {
    super(message);
  }
}
