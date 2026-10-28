package com.mx.cryptomonitor.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@AutoConfigureTracing
@ActiveProfiles("test")
class PrometheusObservabilityIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void prometheusEndpointShouldBeReachableAndExposeMetrics() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus").header("X-Request-Id", "prometheus-it"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "prometheus-it"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "application=\"crypto-portfolio-monitoring\"")));
  }
}
