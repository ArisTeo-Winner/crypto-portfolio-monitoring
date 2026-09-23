package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.PortfolioService;
import com.mx.cryptomonitor.portfolio.domain.exception.InsufficientFundsException;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceAccountingTest {

  private final Logger logger = LoggerFactory.getLogger(PortfolioServiceAccountingTest.class);

  @Mock private PortfolioEntryRepository portfolioEntryRepository;
  @Mock private MarketDataProvider marketDataProvider;
  @Mock private AssetPricePort assetPricePort;
  @Mock private CryptoHistoricalPricePort cryptoHistoricalPricePort;
  @Mock private AssetCatalogQueryPort assetCatalogQueryPort;
  @Mock private TransactionHistoryPort transactionHistoryPort;
  @Mock private FxRatePort fxRatePort;

  @InjectMocks private PortfolioService portfolioService;

  @Test
  void applyTransactionBuyShouldPersistHoldingValuationAndProfitLoss() {
    UUID userId = UUID.randomUUID();
    com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand command =
        new com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand(
            "BTC",
            AssetType.CRYPTO.toString(),
            "BUY",
            null,
            new BigDecimal("0.25"),
            new BigDecimal("22302.0350"),
            new BigDecimal("89208.14"));

    logger.info("List de PortfolioTransactionCommand: {}", command);

    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC"))
        .thenReturn(Optional.empty());
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("90000.00")));
    when(portfolioEntryRepository.save(any(PortfolioEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    portfolioService.applyTransaction(userId, command);

    ArgumentCaptor<PortfolioEntry> captor = ArgumentCaptor.forClass(PortfolioEntry.class);
    verify(portfolioEntryRepository).save(captor.capture());
    PortfolioEntry saved = captor.getValue();

    assertThat(saved.getCurrentValue()).isEqualByComparingTo("22500.00");
    assertThat(saved.getTotalProfitLoss()).isEqualByComparingTo("197.97");
    assertThat(saved.getAveragePricePerUnit()).isEqualByComparingTo("89208.14000000");
  }

  @Test
  void applyTransactionSellShouldReduceInvestedByCostBasisNotSaleValue() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry existing =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(userId)
            .assetSymbol("BTC")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("2.0"))
            .totalInvested(new BigDecimal("180000.00"))
            .averagePricePerUnit(new BigDecimal("90000.00"))
            .build();
    com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand command =
        new com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand(
            "BTC",
            "CRYPTO",
            "SELL",
            null,
            new BigDecimal("0.5"),
            new BigDecimal("47500.00"),
            new BigDecimal("95000.00"));

    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC"))
        .thenReturn(Optional.of(existing));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("95000.00")));
    when(portfolioEntryRepository.save(any(PortfolioEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    portfolioService.applyTransaction(userId, command);

    ArgumentCaptor<PortfolioEntry> captor = ArgumentCaptor.forClass(PortfolioEntry.class);
    verify(portfolioEntryRepository).save(captor.capture());
    PortfolioEntry saved = captor.getValue();

    assertThat(saved.getTotalQuantity()).isEqualByComparingTo("1.5");
    assertThat(saved.getTotalInvested()).isEqualByComparingTo("135000.00");
    assertThat(saved.getAveragePricePerUnit()).isEqualByComparingTo("90000.00000000");
    assertThat(saved.getCurrentValue()).isEqualByComparingTo("142500.00");
    assertThat(saved.getTotalProfitLoss()).isEqualByComparingTo("7500.00");
  }

  @Test
  void applyTransactionSellShouldRejectQuantityAboveHolding() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry existing =
        PortfolioEntry.builder()
            .userId(userId)
            .assetSymbol("BTC")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("0.10"))
            .totalInvested(new BigDecimal("1000.00"))
            .averagePricePerUnit(new BigDecimal("10000.00"))
            .build();
    com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand command =
        new com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand(
            "BTC",
            "CRYPTO",
            "SELL",
            null,
            new BigDecimal("0.50"),
            new BigDecimal("47500.00"),
            new BigDecimal("95000.00"));

    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC"))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> portfolioService.applyTransaction(userId, command))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessageContaining("Cantidad insuficiente");
  }

  @Test
  void applyTransactionTransferOutShouldReduceQuantityAndCostBasis() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry existing =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(userId)
            .assetSymbol("BTC")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("2.0"))
            .totalInvested(new BigDecimal("180000.00"))
            .averagePricePerUnit(new BigDecimal("90000.00"))
            .build();
    com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand command =
        new com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand(
            "BTC",
            "CRYPTO",
            "TRANSFER",
            "TRANSFER_OUT",
            new BigDecimal("0.5"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC"))
        .thenReturn(Optional.of(existing));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("95000.00")));
    when(portfolioEntryRepository.save(any(PortfolioEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    portfolioService.applyTransaction(userId, command);

    ArgumentCaptor<PortfolioEntry> captor = ArgumentCaptor.forClass(PortfolioEntry.class);
    verify(portfolioEntryRepository).save(captor.capture());
    PortfolioEntry saved = captor.getValue();

    assertThat(saved.getTotalQuantity()).isEqualByComparingTo("1.5");
    assertThat(saved.getTotalInvested()).isEqualByComparingTo("135000.00");
    assertThat(saved.getAveragePricePerUnit()).isEqualByComparingTo("90000.00000000");
    assertThat(saved.getCurrentValue()).isEqualByComparingTo("142500.00");
    assertThat(saved.getTotalProfitLoss()).isEqualByComparingTo("7500.00");
  }

  @Test
  void applyTransactionTransferInShouldPreserveAverageCostBasisForExistingHolding() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry existing =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(userId)
            .assetSymbol("BTC")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("2.0"))
            .totalInvested(new BigDecimal("180000.00"))
            .averagePricePerUnit(new BigDecimal("90000.00"))
            .build();
    com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand command =
        new com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand(
            "BTC",
            "CRYPTO",
            "TRANSFER",
            "TRANSFER_IN",
            new BigDecimal("0.5"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC"))
        .thenReturn(Optional.of(existing));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("95000.00")));
    when(portfolioEntryRepository.save(any(PortfolioEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    portfolioService.applyTransaction(userId, command);

    ArgumentCaptor<PortfolioEntry> captor = ArgumentCaptor.forClass(PortfolioEntry.class);
    verify(portfolioEntryRepository).save(captor.capture());
    PortfolioEntry saved = captor.getValue();

    assertThat(saved.getTotalQuantity()).isEqualByComparingTo("2.5");
    assertThat(saved.getTotalInvested()).isEqualByComparingTo("225000.00");
    assertThat(saved.getAveragePricePerUnit()).isEqualByComparingTo("90000.00000000");
    assertThat(saved.getCurrentValue()).isEqualByComparingTo("237500.00");
    assertThat(saved.getTotalProfitLoss()).isEqualByComparingTo("12500.00");
  }

  @Test
  void applyTransactionShouldUseCommandPriceWhenCurrentMarketPriceIsUnavailable() {
    UUID userId = UUID.randomUUID();

    PortfolioTransactionCommand command =
        new PortfolioTransactionCommand(
            "ETH",
            "CRYPTO",
            "BUY",
            null,
            new BigDecimal("0.2"),
            new BigDecimal("400.47"),
            new BigDecimal("1992.35"));

    /*public record PortfolioTransactionCommand(
    String assetSymbol,
    String assetType,
    String transactionType,
    String transferType,
    BigDecimal quantity,
    BigDecimal totalValue,
    BigDecimal pricePerUnit) {}*/

    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "ETH"))
        .thenReturn(Optional.empty());
    when(assetPricePort.getCryptoPriceAmount("ETH"))
        .thenReturn(reactor.core.publisher.Mono.empty());
    when(portfolioEntryRepository.save(any(PortfolioEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    portfolioService.applyTransaction(userId, command);

    ArgumentCaptor<PortfolioEntry> captor = ArgumentCaptor.forClass(PortfolioEntry.class);
    verify(portfolioEntryRepository).save(captor.capture());
    PortfolioEntry saved = captor.getValue();

    assertThat(saved.getLastTransactionPrice()).isEqualByComparingTo("1992.35");
    assertThat(saved.getCurrentValue()).isEqualByComparingTo("398.47");
  }

  @Test
  void applyTransactionShouldFailFastWhenNeitherProviderNorCommandHasPrice() {
    UUID userId = UUID.randomUUID();
    PortfolioTransactionCommand command =
        new PortfolioTransactionCommand(
            "BTC",
            "CRYPTO",
            "BUY",
            null,
            new BigDecimal("0.25"),
            new BigDecimal("22302.0350"),
            null);

    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC"))
        .thenReturn(Optional.empty());

    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.empty());

    assertThatThrownBy(() -> portfolioService.applyTransaction(userId, command))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("No se pudo obtener el precio actual del activo en updatePortfolioEntry()");

    verify(portfolioEntryRepository, never()).save(any(PortfolioEntry.class));
  }
}
