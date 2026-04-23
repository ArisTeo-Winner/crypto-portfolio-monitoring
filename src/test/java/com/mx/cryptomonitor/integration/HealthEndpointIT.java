package com.mx.cryptomonitor.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class HealthEndpointIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void apiHealthShouldBePublicAndSanitized() throws Exception {
    mockMvc
        .perform(get("/api/v1/health").header("X-Request-Id", "health-it"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "health-it"))
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.service").value("crypto-portfolio-monitoring"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  void actuatorReadinessShouldRemainPublic() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }
}
