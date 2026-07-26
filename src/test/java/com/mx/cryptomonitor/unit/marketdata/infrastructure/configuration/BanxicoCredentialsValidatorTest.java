package com.mx.cryptomonitor.unit.marketdata.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.infrastructure.configuration.BanxicoCredentialsValidator;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.BanxicoProperties;

class BanxicoCredentialsValidatorTest {

  private static final String BASE_URL = "https://www.banxico.org.mx";

  @Test
  void failsFastWhenTokenIsBlank() {
    BanxicoCredentialsValidator validator =
        new BanxicoCredentialsValidator(new BanxicoProperties(BASE_URL, ""));

    assertThatThrownBy(validator::validateBanxicoToken)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("BANXICO_TOKEN");
  }

  @Test
  void failsFastWhenTokenIsPlaceholder() {
    BanxicoCredentialsValidator validator =
        new BanxicoCredentialsValidator(new BanxicoProperties(BASE_URL, "__SET_ME__"));

    assertThatThrownBy(validator::validateBanxicoToken).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void passesWhenTokenIsPresent() {
    BanxicoCredentialsValidator validator =
        new BanxicoCredentialsValidator(
            new BanxicoProperties(BASE_URL, "0123456789abcdef0123456789abcdef0123456789abcdef"));

    assertThatCode(validator::validateBanxicoToken).doesNotThrowAnyException();
  }
}
