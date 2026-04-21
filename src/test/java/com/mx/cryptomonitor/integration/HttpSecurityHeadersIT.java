package com.mx.cryptomonitor.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HttpSecurityHeadersIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void actuatorHealthExposesSecurityHeadersAndCorrelationId() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness").header("X-Request-Id", "test-request-id"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "test-request-id"))
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().exists("Permissions-Policy"));
  }

  @Test
  void secureActuatorHealthIncludesHstsAndProbeEndpointsRemainPublic() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness").secure(true))
        .andExpect(status().isOk())
        .andExpect(header().exists("Strict-Transport-Security"))
        .andExpect(header().exists("X-Request-Id"));
  }
}
