package com.mx.cryptomonitor.shared.infrastructure.config;

import java.util.Arrays;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class MetricsConfig {

  @Bean
  MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(Environment environment) {
    return registry ->
        registry
            .config()
            .commonTags(
                "application",
                environment.getProperty("spring.application.name", "crypto-portfolio-monitoring"),
                "profile",
                resolveActiveProfile(environment));
  }

  private String resolveActiveProfile(Environment environment) {
    String[] activeProfiles = environment.getActiveProfiles();
    if (activeProfiles.length == 0) {
      return "default";
    }
    return Arrays.stream(activeProfiles).sorted().findFirst().orElse("default");
  }
}
