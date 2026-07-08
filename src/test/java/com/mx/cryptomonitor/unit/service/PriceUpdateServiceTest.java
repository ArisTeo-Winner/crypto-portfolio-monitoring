package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.portfolio.application.service.PriceUpdateService;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;

@ExtendWith(MockitoExtension.class)
class PriceUpdateServiceTest {

  @Mock private MarketDataProvider marketDataProvider;
  @Mock private AssetPricePort assetPricePort;
  @Mock private PortfolioEntryRepository portfolioEntryRepository;

  @InjectMocks private PriceUpdateService priceUpdateService;

  @Test
  void updatePricesShouldDoNothingWhenThereAreNoTrackedEntries() {
    when(portfolioEntryRepository.findAll()).thenReturn(java.util.List.of());

    priceUpdateService.updatePrices();

    verify(marketDataProvider, never()).getLatest(any());
    verify(assetPricePort, never()).getCryptoPriceAmount(any());
    verify(portfolioEntryRepository, never()).save(any());
  }

  @Test
  void updatePricesShouldSkipCryptoEntryWhenProviderHasNoPrice() {
    PortfolioEntry btcEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .assetSymbol("BTC")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.50000000"))
            .totalInvested(new BigDecimal("120000.00"))
            .averagePricePerUnit(new BigDecimal("80000.00000000"))
            .build();
    when(portfolioEntryRepository.findAll()).thenReturn(java.util.List.of(btcEntry));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.empty());

    priceUpdateService.updatePrices();

    verify(portfolioEntryRepository, never()).save(any());
  }

  @Test
  void updatePricesShouldPersistCurrentValueProfitLossAndTimestampForEachEntry() {
    PortfolioEntry btcEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .assetSymbol("BTC")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.50000000"))
            .totalInvested(new BigDecimal("120000.00"))
            .averagePricePerUnit(new BigDecimal("80000.00000000"))
            .lastUpdated(LocalDateTime.now().minusDays(1))
            .build();

    when(portfolioEntryRepository.findAll()).thenReturn(java.util.List.of(btcEntry));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("95000.00")));
    when(portfolioEntryRepository.save(any(PortfolioEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    priceUpdateService.updatePrices();

    ArgumentCaptor<PortfolioEntry> captor = ArgumentCaptor.forClass(PortfolioEntry.class);
    verify(portfolioEntryRepository).save(captor.capture());
    PortfolioEntry saved = captor.getValue();

    assertThat(saved.getCurrentValue()).isEqualByComparingTo("142500.00000000");
    assertThat(saved.getTotalProfitLoss()).isEqualByComparingTo("22500.00000000");
    assertThat(saved.getLastUpdated()).isNotNull();
  }

  @Test
  void updatePricesShouldContinueWithRemainingEntriesWhenOneProviderCallFails() {
    PortfolioEntry btcEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .assetSymbol("BTC")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.50000000"))
            .totalInvested(new BigDecimal("120000.00"))
            .averagePricePerUnit(new BigDecimal("80000.00000000"))
            .build();
    PortfolioEntry ethEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .assetSymbol("ETH")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("10.00000000"))
            .totalInvested(new BigDecimal("20000.00"))
            .averagePricePerUnit(new BigDecimal("2000.00000000"))
            .build();

    when(portfolioEntryRepository.findAll()).thenReturn(java.util.List.of(btcEntry, ethEntry));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(
            reactor.core.publisher.Mono.error(
                new org.springframework.web.reactive.function.client.WebClientRequestException(
                    new java.net.UnknownHostException("pro-api.coinmarketcap.com"),
                    org.springframework.http.HttpMethod.GET,
                    java.net.URI.create("https://pro-api.coinmarketcap.com"),
                    new org.springframework.http.HttpHeaders())));
    when(assetPricePort.getCryptoPriceAmount("ETH"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("2500.00")));
    when(portfolioEntryRepository.save(any(PortfolioEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    priceUpdateService.updatePrices();

    ArgumentCaptor<PortfolioEntry> captor = ArgumentCaptor.forClass(PortfolioEntry.class);
    verify(portfolioEntryRepository).save(captor.capture());
    assertThat(captor.getValue().getAssetSymbol()).isEqualTo("ETH");
  }
}
