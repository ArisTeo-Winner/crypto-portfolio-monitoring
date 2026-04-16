package com.mx.cryptomonitor.portfolio.domain.exception;

public class PortfolioEntryNotFoundException extends RuntimeException {

  public PortfolioEntryNotFoundException(String message) {
    super(message);
  }
}
