package com.mx.cryptomonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.PortfolioService;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

  @Mock private PortfolioEntryRepository portfolioEntryRepository;
  @Mock private MarketDataProvider marketDataProvider;
  @Mock private AssetPricePort assetPricePort;
  @Mock private CryptoHistoricalPricePort cryptoHistoricalPricePort;
  @Mock private AssetCatalogQueryPort assetCatalogQueryPort;
  @Mock private TransactionHistoryPort transactionHistoryPort;

  @InjectMocks private PortfolioService portfolioService;

  @Test
  void shouldReturnPortfolioEntryByUserAndSymbol() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry entry =
        PortfolioEntry.builder()
            .assetSymbol("BTC")
            .assetType("CRYPTO")
            .totalQuantity(BigDecimal.ONE)
            .totalInvested(new BigDecimal("95000.00"))
            .build();
    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC"))
        .thenReturn(Optional.of(entry));

    Optional<PortfolioEntry> result =
        portfolioService.getPortfolioEntryByUserAndSymbol(userId, "BTC");

    assertThat(result).contains(entry);
    verify(portfolioEntryRepository).findByUserIdAndAssetSymbol(userId, "BTC");
  }

  @Test
  void shouldReturnCurrentCryptoPrice() {
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("95000.00")));

    Optional<BigDecimal> result = portfolioService.getCurrentCryptoPrice("BTC");

    assertThat(result).contains(new BigDecimal("95000.00"));
    verify(assetPricePort).getCryptoPriceAmount("BTC");
  }

  @Test
  void shouldReturnCurrentTimestamp() {
    assertThat(portfolioService.now()).isNotNull();
  }
}
