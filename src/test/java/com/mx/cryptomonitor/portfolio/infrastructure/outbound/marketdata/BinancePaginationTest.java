package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolutionStrategy;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BinancePaginationTest {

  @Mock WebClient webClient;
  @Mock RequestHeadersUriSpec<?> uriSpec;
  @Mock RequestHeadersSpec<?> headersSpec;
  @Mock ResponseSpec responseSpec;

  BinanceMarketPriceHistoryAdapter adapter;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    adapter = new BinanceMarketPriceHistoryAdapter(webClient);
    when(webClient.get()).thenReturn((RequestHeadersUriSpec) uriSpec);
    when(uriSpec.uri(any(java.util.function.Function.class)))
        .thenReturn((RequestHeadersSpec) headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
  }

  @Test
  void fetchPriceHistory_returnsMappedPricePoints() {
    List<List<Object>> klines =
        buildKlines(10, Instant.parse("2024-01-01T00:00:00Z"), Duration.ofDays(1));
    when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
        .thenReturn(Mono.just(klines));

    ChartResolution cr =
        new ChartResolutionStrategy()
            .resolve(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-10T00:00:00Z"));

    List<PricePoint> result = adapter.fetchPriceHistory(AssetType.CRYPTO, "BTC", cr);

    assertThat(result).hasSize(10);
    assertThat(result).isSortedAccordingTo(java.util.Comparator.comparing(PricePoint::time));
  }

  @Test
  void fetchPriceHistory_emptyResponse_returnsEmptyList() {
    when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
        .thenReturn(Mono.just(List.of()));

    ChartResolution cr =
        new ChartResolutionStrategy()
            .resolve(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-05T00:00:00Z"));

    List<PricePoint> result = adapter.fetchPriceHistory(AssetType.CRYPTO, "BTC", cr);

    assertThat(result).isEmpty();
  }

  @Test
  void supportsOnlyCrypto() {
    assertThat(adapter.supports(AssetType.CRYPTO)).isTrue();
    assertThat(adapter.supports(AssetType.STOCK)).isFalse();
    assertThat(adapter.supports(AssetType.ETF)).isFalse();
  }

  private List<List<Object>> buildKlines(int count, Instant start, Duration interval) {
    List<List<Object>> klines = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Instant time = start.plus(interval.multipliedBy(i));
      List<Object> kline = new ArrayList<>();
      kline.add(time.toEpochMilli()); // openTime
      kline.add("50000.00"); // open
      kline.add("51000.00"); // high
      kline.add("49000.00"); // low
      kline.add("50500.00"); // close
      kline.add("100.0"); // volume
      kline.add(time.toEpochMilli() + interval.toMillis() - 1); // closeTime
      kline.add("5000000.0"); // quoteVolume
      kline.add(1000); // trades
      kline.add("50.0"); // takerBuyBase
      kline.add("2500000.0"); // takerBuyQuote
      kline.add("0"); // ignore
      klines.add(kline);
    }
    return klines;
  }
}
