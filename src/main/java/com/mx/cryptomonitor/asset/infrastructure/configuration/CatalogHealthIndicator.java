package com.mx.cryptomonitor.asset.infrastructure.configuration;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CatalogHealthIndicator implements HealthIndicator {

  private final CatalogStorePort redisService;

  @Override
  public Health health() {
    if (redisService.isCatalogLoaded()) {
      return Health.up().withDetail("catalog", "loaded").build();
    }
    return Health.down()
        .withDetail("catalog", "not loaded")
        .withDetail("action", "check FMP_API_KEY or trigger warm-up manually")
        .build();
  }
}
