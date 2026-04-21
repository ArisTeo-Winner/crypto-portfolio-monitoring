package com.mx.cryptomonitor.unit.shared.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.user.application.dto.request.LoginRequest;

class LoginRequestToStringRedactionUnitTest {

  @Test
  void toString_mustRedactPassword() {
    String password = "Password123!";
    LoginRequest request = new LoginRequest("leo@example.com", password);

    String rendered = request.toString();

    assertThat(rendered).doesNotContain(password);
    assertThat(rendered).contains("<redacted>");
  }
}
