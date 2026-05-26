package com.mx.cryptomonitor.marketdata.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Intervalos temporales soportados por Twelve Data en el endpoint {@code /time_series}.
 *
 * <p>Cada constante expone su código nativo ({@link #apiCode}) para pasarlo directamente como
 * query-param {@code interval} en la llamada HTTP.
 */
public enum StockInterval {

  ONE_MIN("1min"),
  FIVE_MIN("5min"),
  FIFTEEN_MIN("15min"),
  THIRTY_MIN("30min"),
  FORTY_FIVE_MIN("45min"),
  ONE_HOUR("1h"),
  TWO_HOUR("2h"),
  FOUR_HOUR("4h"),
  EIGHT_HOUR("8h"),
  ONE_DAY("1day"),
  ONE_WEEK("1week"),
  ONE_MONTH("1month");

  private final String apiCode;

  StockInterval(String apiCode) {
    this.apiCode = apiCode;
  }

  /** Código que se envía a Twelve Data como valor del param {@code interval}. */
  public String getApiCode() {
    return apiCode;
  }

  /**
   * Busca la constante cuyo {@link #apiCode} coincide con el string recibido
   * (insensible a mayúsculas).
   *
   * @return {@link Optional#empty()} si el valor no corresponde a ningún intervalo válido
   */
  public static Optional<StockInterval> fromCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(i -> i.apiCode.equalsIgnoreCase(code.trim()))
        .findFirst();
  }

  /** Todos los códigos válidos como string separado por comas — para mensajes de error. */
  public static String validCodes() {
    return Arrays.stream(values())
        .map(StockInterval::getApiCode)
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
  }
}
