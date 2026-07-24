package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import com.mx.cryptomonitor.integration.support.ContainersConfig;
import com.mx.cryptomonitor.marketdata.application.port.out.GovBondRatePort;
import com.mx.cryptomonitor.marketdata.application.service.BanxicoCurveService;
import com.mx.cryptomonitor.marketdata.domain.repository.BanxicoCetesRateRepository;

/**
 * Verifica el cache Redis+Postgres de la curva CETES contra infraestructura real, incluyendo que un
 * fallo de Banxico durante el refresco conserva la curva previa (patron de CatalogSyncService).
 */
@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "banxico.curve.refresh.enabled=true")
class BanxicoCurveServiceIT {

  @Autowired private BanxicoCurveService curveService;
  @Autowired private BanxicoCetesRateRepository rateRepository;
  @Autowired private StringRedisTemplate redisTemplate;

  @MockBean private GovBondRatePort banxicoRateAdapter;

  @BeforeEach
  void cleanState() {
    rateRepository.deleteAll();
    redisTemplate.delete("banxico:cetes:curve");
    redisTemplate.delete("banxico:cetes:auctionDate");
  }

  @Test
  void refreshCurvePersistsToPostgresAndRedis() {
    when(banxicoRateAdapter.getCetesCurve())
        .thenReturn(Map.of(28, new BigDecimal("6.18"), 91, new BigDecimal("6.49")));

    curveService.refreshCurve();

    assertThat(rateRepository.findAll()).hasSize(2);
    Map<Object, Object> redisEntries = redisTemplate.opsForHash().entries("banxico:cetes:curve");
    assertThat(redisEntries).hasSize(2);
    assertThat(redisEntries.get("28")).isEqualTo("6.18");
    assertThat(redisEntries.get("91")).isEqualTo("6.49");
  }

  @Test
  void refreshFailurePreservesThePreviousCurve() {
    when(banxicoRateAdapter.getCetesCurve()).thenReturn(Map.of(28, new BigDecimal("6.18")));
    curveService.refreshCurve();

    reset(banxicoRateAdapter);
    when(banxicoRateAdapter.getCetesCurve()).thenThrow(new RuntimeException("Banxico caido"));
    curveService.refreshCurve();

    assertThat(rateRepository.findAll()).hasSize(1);
    Map<Object, Object> redisEntries = redisTemplate.opsForHash().entries("banxico:cetes:curve");
    assertThat(redisEntries.get("28")).isEqualTo("6.18");
  }

  @Test
  void getCurveFallsBackToPostgresWhenRedisIsEmpty() {
    when(banxicoRateAdapter.getCetesCurve()).thenReturn(Map.of(182, new BigDecimal("6.75")));
    curveService.refreshCurve();

    redisTemplate.delete("banxico:cetes:curve");
    reset(banxicoRateAdapter);

    Map<Integer, BigDecimal> curve = curveService.getCurve();

    assertThat(curve.get(182)).isEqualByComparingTo("6.75");
  }
}
