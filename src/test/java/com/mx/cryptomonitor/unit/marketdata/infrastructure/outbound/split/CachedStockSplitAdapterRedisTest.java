package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.StockSplitEntity;
import com.mx.cryptomonitor.marketdata.domain.model.StockSplitSyncEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.StockSplitRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.StockSplitSyncRepository;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.split.CachedStockSplitAdapter;

@ExtendWith(MockitoExtension.class)
class CachedStockSplitAdapterRedisTest {

  @Mock private StockSplitRepository stockSplitRepository;
  @Mock private StockSplitSyncRepository stockSplitSyncRepository;
  @Mock private StockSplitProviderPort stockSplitProvider;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private CachedStockSplitAdapter adapter;

  private static final String KEY = "marketdata:splits:WETO";

  @BeforeEach
  void setUp() {
    adapter =
        new CachedStockSplitAdapter(
            stockSplitRepository,
            stockSplitSyncRepository,
            stockSplitProvider,
            redisTemplate,
            objectMapper);
    ReflectionTestUtils.setField(adapter, "cacheTtlHours", 24L);
  }

  @Test
  void redisHitServesWithoutTouchingPostgresOrProvider() throws Exception {
    String json =
        objectMapper.writeValueAsString(
            List.of(new StockSplitData("WETO", LocalDate.of(2026, 8, 3), new BigDecimal("0.01"))));
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn(json);

    List<StockSplitData> result = adapter.splitsFor("weto");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).ticker()).isEqualTo("WETO");
    assertThat(result.get(0).shareMultiplier()).isEqualByComparingTo("0.01");
    verifyNoInteractions(stockSplitRepository, stockSplitSyncRepository, stockSplitProvider);
  }

  @Test
  void redisMissReadsPostgresAndPopulatesRedis() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn(null);
    when(stockSplitSyncRepository.findByTicker("WETO"))
        .thenReturn(
            Optional.of(
                StockSplitSyncEntity.builder()
                    .ticker("WETO")
                    .syncedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                    .build()));
    when(stockSplitRepository.findByTickerOrderByExecutionDateDesc("WETO"))
        .thenReturn(
            List.of(
                StockSplitEntity.builder()
                    .ticker("WETO")
                    .executionDate(LocalDate.of(2026, 8, 3))
                    .shareMultiplier(new BigDecimal("0.01"))
                    .provider("MASSIVE")
                    .build()));

    List<StockSplitData> result = adapter.splitsFor("WETO");

    assertThat(result).hasSize(1);
    verifyNoInteractions(stockSplitProvider); // sync marker fresco => no pega al proveedor
    verify(valueOperations).set(eq(KEY), anyString(), any(Duration.class));
  }
}
