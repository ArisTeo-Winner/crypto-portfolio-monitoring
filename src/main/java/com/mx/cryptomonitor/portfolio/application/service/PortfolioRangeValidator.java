package com.mx.cryptomonitor.portfolio.application.service;

import org.springframework.stereotype.Component;

@Component
public class PortfolioRangeValidator {

  public static final int DEFAULT_RANGE_DAYS = 180;
  public static final int MAX_RANGE_DAYS = 365;

  public int validate(String range) {
    if (range == null || range.isBlank()) {
      return DEFAULT_RANGE_DAYS;
    }
    try {
      int days = Integer.parseInt(range.trim());
      if (days <= 0 || days > MAX_RANGE_DAYS) {
        throw new IllegalArgumentException("range must be between 1 and 365 days");
      }
      return days;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("range must be a number of days", ex);
    }
  }
}
