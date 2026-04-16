package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
  public Money {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    if (currency.isBlank()) {
      throw new IllegalArgumentException("currency must not be blank");
    }
  }
}
