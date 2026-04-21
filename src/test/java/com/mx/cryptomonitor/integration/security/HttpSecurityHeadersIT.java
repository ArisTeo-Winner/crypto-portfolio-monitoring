package com.mx.cryptomonitor.integration.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class HttpSecurityHeadersIT {

  private static final String EXPECTED_CSP =
      "default-src 'self'; "
          + "script-src 'self' 'unsafe-inline'; "
          + "style-src 'self' 'unsafe-inline'; "
          + "img-src 'self' data: https:; "
          + "font-src 'self' data:; "
          + "connect-src 'self' https:; "
          + "object-src 'none'; "
          + "frame-ancestors 'none'; "
          + "base-uri 'self'; "
          + "form-action 'self'";
  private static final String EXPECTED_PERMISSIONS_POLICY =
      "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
          + "microphone=(), payment=(), usb=()";

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldExposeAppSecSecurityHeadersOnPublicApiResponses() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Security-Policy", EXPECTED_CSP))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Permissions-Policy", EXPECTED_PERMISSIONS_POLICY));
  }

  @Test
  void shouldExposeHstsOnSecureRequests() throws Exception {
    mockMvc
        .perform(get("/actuator/health").secure(true))
        .andExpect(status().isOk())
        .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
        .andExpect(
            header().string("Strict-Transport-Security", containsString("includeSubDomains")));
  }
}
