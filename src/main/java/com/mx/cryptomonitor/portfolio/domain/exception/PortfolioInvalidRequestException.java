package com.mx.cryptomonitor.portfolio.domain.exception;

public class PortfolioInvalidRequestException extends RuntimeException {

  public PortfolioInvalidRequestException(String message) {
    super(message);
  }

  public PortfolioInvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
