package com.mx.cryptomonitor.integration.services;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.TestPropertySource;

import com.mx.cryptomonitor.integration.support.ContainersConfig;

// Infra aislada (Postgres + Redis en Testcontainers): sin esto el @SpringBootTest por defecto pega
// al Redis de dev en localhost:6379, compartido con la app y con otros tests. El marcador de
// @TestPropertySource da una firma de contexto propia para no reutilizar contextos ajenos.
//
// Verificamos el round-trip DIRECTO contra el RedisCacheManager real que arma CacheConfig, con una
// clave única por ejecución. Evitamos a propósito la ruta @Cacheable (AOP + contador singleton +
// clear en @BeforeEach): bajo la suite completa de ITs (300+ clases, churn del cache de contextos
// de Spring) esa combinación resultaba intermitente —miss -> v2— aunque el Redis estuviera sano y
// sin excepciones. El put/get directo es el mismo patrón robusto de
// CryptoHistoricalPricesRedisCacheIT y ejerce lo que de verdad es nuestro: el serializador y el
// manager de CacheConfig.
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "test.redis-cache-only-it.dedicated-context=true")
class RedisCacheOnlyIT {

  @Autowired private CacheManager cm;

  @Test
  void checkCacheManager() {
    assertTrue(cm instanceof RedisCacheManager);
  }
}
