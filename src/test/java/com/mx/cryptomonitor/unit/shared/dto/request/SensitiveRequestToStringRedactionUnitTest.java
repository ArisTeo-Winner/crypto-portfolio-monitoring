package com.mx.cryptomonitor.unit.shared.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.user.application.dto.request.PasswordChangeRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;

class SensitiveRequestToStringRedactionUnitTest {

  @Test
  void passwordChangeRequestToString_mustRedactSecrets() {
    String currentPassword = "Current123!";
    String newPassword = "NewPassword123!";
    PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

    String rendered = request.toString();

    assertThat(rendered).doesNotContain(currentPassword);
    assertThat(rendered).doesNotContain(newPassword);
    assertThat(rendered).contains("<redacted>");
  }

  @Test
  void userRegistrationRequestToString_mustRedactCredentials() {
    String password = "Password123!";
    UserRegistrationRequest request =
        new UserRegistrationRequest(
            "leo_user",
            "leo@example.com",
            password,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    String rendered = request.toString();

    assertThat(rendered).contains("username=leo_user");
    assertThat(rendered).contains("email=leo@example.com");
    assertThat(rendered).doesNotContain(password);
    assertThat(rendered).contains("<redacted>");
  }
}
