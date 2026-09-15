package com.mx.cryptomonitor.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogRefreshPort;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionOrigin;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.UpdateTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionDetailsResponse;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.application.port.out.PortfolioProjectionSyncPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionAuditPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionRegistrationPort;
import com.mx.cryptomonitor.transaction.application.service.TransactionIdempotencyService;
import com.mx.cryptomonitor.transaction.application.service.TransactionRealizedPnlService;
import com.mx.cryptomonitor.transaction.application.service.TransactionService;
import com.mx.cryptomonitor.transaction.domain.exception.TransactionNotFoundException;
import com.mx.cryptomonitor.transaction.domain.friction.FrictionBreakdown;
import com.mx.cryptomonitor.transaction.domain.friction.FrictionSide;
import com.mx.cryptomonitor.transaction.domain.friction.GbmFrictionCalculator;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.ImportSource;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

  private static final Sort RECENT_FIRST_SORT =
      Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt"));
  private static final String IDEMPOTENCY_KEY = "test-idempotency-key";

  @Mock private TransactionRepository transactionRepository;
  @Mock private TransactionRegistrationPort transactionRegistrationPort;
  @Mock private PortfolioProjectionSyncPort portfolioProjectionSyncPort;
  @Mock private TransactionIdempotencyService transactionIdempotencyService;
  @Mock private TransactionAuditPort transactionAuditPort;
  @Mock private TransactionRealizedPnlService transactionRealizedPnlService;
  @Mock private DividendDetailRepository dividendDetailRepository;
  @Mock private AssetCatalogQueryPort assetCatalogQueryPort;
  @Mock private AssetCatalogRefreshPort assetCatalogRefreshPort;

  private final TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);

  @InjectMocks private TransactionService transactionService;

  private UUID userId;
  private TransactionResponse transactionResponse;
  private Transaction transaction;

  @BeforeEach
  void setUp() {
    transactionService =
        new TransactionService(
            transactionRepository,
            transactionRegistrationPort,
            portfolioProjectionSyncPort,
            transactionMapper,
            transactionIdempotencyService,
            transactionAuditPort,
            transactionRealizedPnlService,
            dividendDetailRepository,
            assetCatalogQueryPort,
            new com.mx.cryptomonitor.transaction.domain.friction.GbmFrictionCalculator(),
            assetCatalogRefreshPort);
    lenient()
        .when(assetCatalogQueryPort.findNameBySymbol(anyString()))
        .thenReturn(Optional.empty());
    lenient().when(assetCatalogQueryPort.findLogosBySymbols(any())).thenReturn(Map.of());
    lenient()
        .doAnswer(invocation -> invocation.<Supplier<TransactionResponse>>getArgument(4).get())
        .when(transactionIdempotencyService)
        .executeForTransactionResponse(any(UUID.class), anyString(), anyString(), any(), any());
    lenient()
        .doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(4).run();
              return null;
            })
        .when(transactionIdempotencyService)
        .executeForVoid(any(UUID.class), anyString(), anyString(), any(), any());
    userId = UUID.randomUUID();
    transactionResponse =
        new TransactionResponse(
            UUID.randomUUID(),
            "BTC",
            AssetType.CRYPTO.toString(),
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("95000.00"),
            new BigDecimal("47500.00"),
            OffsetDateTime.of(2026, 3, 10, 1, 0, 0, 0, ZoneOffset.UTC),
            new BigDecimal("10.00"),
            "buy btc",
            OffsetDateTime.of(2026, 3, 10, 1, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2026, 3, 10, 1, 0, 0, 0, ZoneOffset.UTC),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    transaction = new Transaction();
    transaction.setTransactionId(UUID.randomUUID());
    transaction.setUser(com.mx.cryptomonitor.user.domain.model.User.builder().id(userId).build());
    transaction.setAssetSymbol("BTC");
    transaction.setAssetType(AssetType.CRYPTO);
    transaction.setTransactionType("BUY");
    transaction.setQuantity(new BigDecimal("0.50"));
    transaction.setPricePerUnit(new BigDecimal("95000.00"));
    transaction.setTotalValue(new BigDecimal("47500.00"));
    transaction.setFee(new BigDecimal("10.00"));
    transaction.setTransactionDate(OffsetDateTime.of(2026, 3, 10, 1, 0, 0, 0, ZoneOffset.UTC));
    transaction.setNotes("buy btc");
    transaction.setCreatedAt(OffsetDateTime.of(2026, 3, 10, 1, 0, 0, 0, ZoneOffset.UTC));
    transaction.setUpdatedAt(OffsetDateTime.of(2026, 3, 10, 1, 0, 0, 0, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("registerTransaction: delegates to transaction registration port")
  void registerTransactionDelegates() {
    TransactionRequest request =
        new TransactionRequest(
            "BTC",
            AssetType.CRYPTO,
            "BUY",
            BigDecimal.ONE,
            new BigDecimal("10"),
            new BigDecimal("10"),
            OffsetDateTime.now(ZoneOffset.UTC),
            BigDecimal.ONE,
            "Notes");

    when(transactionRegistrationPort.registerTransaction(userId, request))
        .thenReturn(transactionResponse);

    TransactionResponse result =
        transactionService.registerTransaction(userId, request, IDEMPOTENCY_KEY);

    assertThat(result).isEqualTo(transactionResponse);
    verify(transactionRegistrationPort).registerTransaction(userId, request);
  }

  @Test
  @DisplayName("registerBuyTransaction: computes gross amount in backend")
  void registerBuyTransactionComputesGrossAmount() {
    BuyTransactionRequest request =
        new BuyTransactionRequest(
            "btc",
            "CRYPTO",
            new BigDecimal("0.25"),
            new BigDecimal("89208.14"),
            new BigDecimal("0.50"),
            OffsetDateTime.of(2026, 1, 24, 17, 55, 0, 0, ZoneOffset.UTC),
            "Compra manual",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(transactionRegistrationPort.registerTransaction(eq(userId), any(TransactionRequest.class)))
        .thenReturn(transactionResponse);

    transactionService.registerBuyTransaction(userId, request, IDEMPOTENCY_KEY);

    ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
    verify(transactionRegistrationPort).registerTransaction(eq(userId), captor.capture());
    TransactionRequest mapped = captor.getValue();
    assertThat(mapped.assetSymbol()).isEqualTo("BTC");
    assertThat(mapped.assetType()).isEqualTo(AssetType.CRYPTO);
    assertThat(mapped.transactionType()).isEqualTo("BUY");
    assertThat(mapped.totalValue()).isEqualByComparingTo("22302.0350");
    assertThat(mapped.transferType()).isNull();
  }

  @Test
  @DisplayName("registerSellTransaction: computes gross amount in backend")
  void registerSellTransactionComputesGrossAmount() {
    SellTransactionRequest request =
        new SellTransactionRequest(
            "btc",
            "CRYPTO",
            new BigDecimal("0.10"),
            new BigDecimal("70392.39"),
            new BigDecimal("1.25"),
            OffsetDateTime.of(2026, 3, 10, 2, 11, 0, 0, ZoneOffset.UTC),
            "Venta parcial",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(transactionRegistrationPort.registerTransaction(eq(userId), any(TransactionRequest.class)))
        .thenReturn(transactionResponse);

    transactionService.registerSellTransaction(userId, request, IDEMPOTENCY_KEY);

    ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
    verify(transactionRegistrationPort).registerTransaction(eq(userId), captor.capture());
    TransactionRequest mapped = captor.getValue();
    assertThat(mapped.transactionType()).isEqualTo("SELL");
    assertThat(mapped.totalValue()).isEqualByComparingTo("7039.2390");
    assertThat(mapped.fee()).isEqualByComparingTo("1.25");
  }

  @Test
  @DisplayName("registerTransferTransaction: maps transfer type without price input")
  void registerTransferTransactionMapsTransferType() {
    TransferTransactionRequest request =
        new TransferTransactionRequest(
            "btc",
            "CRYPTO",
            "TRANSFER_IN",
            new BigDecimal("0.25"),
            new BigDecimal("0.0002"),
            OffsetDateTime.of(2026, 3, 10, 2, 11, 0, 0, ZoneOffset.UTC),
            "Transferencia desde Bitget");

    when(transactionRegistrationPort.registerTransaction(eq(userId), any(TransactionRequest.class)))
        .thenReturn(transactionResponse);

    transactionService.registerTransferTransaction(userId, request, IDEMPOTENCY_KEY);

    ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
    verify(transactionRegistrationPort).registerTransaction(eq(userId), captor.capture());
    TransactionRequest mapped = captor.getValue();
    assertThat(mapped.transactionType()).isEqualTo("TRANSFER");
    assertThat(mapped.transferType()).isEqualTo("TRANSFER_IN");
    assertThat(mapped.pricePerUnit()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(mapped.totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("getTransactionDetails: returns buy details with net amount")
  void getTransactionDetailsForBuy() {
    when(transactionRepository.findByTransactionIdAndUserId(transaction.getTransactionId(), userId))
        .thenReturn(Optional.of(transaction));

    TransactionDetailsResponse result =
        transactionService.getTransactionDetails(userId, transaction.getTransactionId());

    assertThat(result.amountLabel()).isEqualTo("Total Spent");
    assertThat(result.grossAmount()).isEqualByComparingTo("47500.00");
    assertThat(result.netAmount()).isEqualByComparingTo("47510.00");
    assertThat(result.transferType()).isNull();
    assertThat(result.source()).isEqualTo("MANUAL");

    assertThat(result.frictionBreakdown()).isNotNull();
    assertThat(result.frictionBreakdown().grossAmount()).isEqualByComparingTo("47500.00");
    assertThat(result.frictionBreakdown().totalFrictionCost()).isEqualByComparingTo("10.00");
    assertThat(result.frictionBreakdown().finalNetCost()).isEqualByComparingTo("47510.00");
    assertThat(result.frictionBreakdown().adjustedUnitPrice()).isEqualByComparingTo("95020.00");
    assertThat(result.frictionBreakdown().brokerCommission()).isNull();
    assertThat(result.frictionBreakdown().reviewStatus()).isNull();
  }

  @Test
  @DisplayName("getTransactionDetails: exposes broker friction breakdown when present")
  void getTransactionDetailsExposesFrictionBreakdown() {
    FrictionBreakdown breakdown =
        new GbmFrictionCalculator()
            .driveWealth(
                FrictionSide.BUY,
                new BigDecimal("0.95368698"),
                new BigDecimal("103.76"),
                new BigDecimal("0.25"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("104.01"));
    UUID txId = UUID.randomUUID();
    Transaction tx =
        Transaction.builder()
            .transactionId(txId)
            .assetSymbol("CRCL")
            .assetType(AssetType.STOCK)
            .transactionType("BUY")
            .quantity(new BigDecimal("0.95368698"))
            .pricePerUnit(new BigDecimal("108.7988"))
            .totalValue(new BigDecimal("103.76"))
            .build();
    tx.applyFriction(breakdown);
    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(tx));

    TransactionDetailsResponse result = transactionService.getTransactionDetails(userId, txId);

    assertThat(result.frictionBreakdown().brokerCommission()).isEqualByComparingTo("0.25");
    assertThat(result.frictionBreakdown().brokerIva()).isEqualByComparingTo("0.00");
    assertThat(result.frictionBreakdown().otherFees()).isEqualByComparingTo("0.00");
    assertThat(result.frictionBreakdown().totalFrictionCost()).isEqualByComparingTo("0.25");
    assertThat(result.frictionBreakdown().finalNetCost()).isEqualByComparingTo("104.01");
    assertThat(result.frictionBreakdown().reviewStatus())
        .isEqualTo(com.mx.cryptomonitor.transaction.domain.friction.ReviewStatus.OK);
  }

  @Test
  @DisplayName("getTransactionDetails: returns real import source instead of hardcoded MANUAL")
  void getTransactionDetailsReturnsRealImportSource() {
    UUID txId = UUID.randomUUID();
    Transaction tx =
        Transaction.builder()
            .transactionId(txId)
            .assetSymbol("CRCL")
            .assetType(AssetType.STOCK)
            .transactionType("BUY")
            .quantity(new BigDecimal("1"))
            .pricePerUnit(new BigDecimal("100"))
            .totalValue(new BigDecimal("100"))
            .importSource(ImportSource.DRIVEWEALTH)
            .build();
    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(tx));

    TransactionDetailsResponse result = transactionService.getTransactionDetails(userId, txId);

    assertThat(result.source()).isEqualTo("DRIVEWEALTH");
  }

  @Test
  @DisplayName("tagImportSource: sets provenance on an existing transaction")
  void tagImportSourceSetsProvenance() {
    UUID txId = UUID.randomUUID();
    Transaction tx = Transaction.builder().transactionId(txId).build();
    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(tx));

    transactionService.tagImportSource(userId, txId, TransactionOrigin.GBM_STATEMENT);

    assertThat(tx.getImportSource()).isEqualTo(ImportSource.GBM_STATEMENT);
    verify(transactionRepository).save(tx);
  }

  @Test
  @DisplayName("getTransactionsUser: filter all parameters")
  void getTransactionsUserAllParams() {
    when(transactionRepository.findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(
            userId, "BTC", AssetType.CRYPTO, "BUY"))
        .thenReturn(Collections.singletonList(transaction));

    List<TransactionResponse> result =
        transactionService.getTransactionsUser(userId, "BTC", "CRYPTO", "BUY");

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("getTransactionsUser: filter by symbol only")
  void getTransactionsUserSymbolOnly() {
    when(transactionRepository.findByUserIdAndAssetSymbol(userId, "BTC", RECENT_FIRST_SORT))
        .thenReturn(Collections.singletonList(transaction));

    List<TransactionResponse> result =
        transactionService.getTransactionsUser(userId, "BTC", null, null);

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("getTransactionsUser: filter by asset type only")
  void getTransactionsUserAssetTypeOnly() {
    when(transactionRepository.findByUserIdAndAssetType(
            userId, AssetType.CRYPTO, RECENT_FIRST_SORT))
        .thenReturn(Collections.singletonList(transaction));

    List<TransactionResponse> result =
        transactionService.getTransactionsUser(userId, null, "CRYPTO", "");

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("getTransactionsUser: filter by transaction type only")
  void getTransactionsUserTransactionTypeOnly() {
    when(transactionRepository.findByUserIdAndTransactionType(userId, "BUY", RECENT_FIRST_SORT))
        .thenReturn(Collections.singletonList(transaction));

    List<TransactionResponse> result =
        transactionService.getTransactionsUser(userId, "", "", "BUY");

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("getTransactionsUser: no filters returns all")
  void getTransactionsUserNoFilters() {
    when(transactionRepository.findByUserId(userId, RECENT_FIRST_SORT))
        .thenReturn(Collections.singletonList(transaction));

    List<TransactionResponse> result =
        transactionService.getTransactionsUser(userId, null, null, null);

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("deleteTransactionById: success")
  void deleteTransactionByIdSuccess() {
    UUID txId = UUID.randomUUID();
    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(transaction));

    transactionService.deleteTransactionById(userId, txId, IDEMPOTENCY_KEY);

    verify(transactionRepository).deleteById(transaction.getTransactionId());
    verify(transactionIdempotencyService)
        .forgetByResultTransactionId(transaction.getTransactionId());
    verify(portfolioProjectionSyncPort).reconcileUserPortfolio(userId);
  }

  @Test
  @DisplayName("updateTransaction: recomputes total value and reconciles portfolio")
  void updateTransactionRecomputesTotalValueAndReconcilesPortfolio() {
    UUID txId = transaction.getTransactionId();
    UpdateTransactionRequest request =
        new UpdateTransactionRequest(
            "eth",
            "CRYPTO",
            new BigDecimal("1.25"),
            new BigDecimal("3200.50"),
            OffsetDateTime.of(2026, 3, 12, 8, 45, 0, 0, ZoneOffset.UTC),
            new BigDecimal("1.10"),
            "updated tx",
            null);

    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(transaction));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Transaction.class));
    when(portfolioProjectionSyncPort.resolvePortfolioEntryId(userId, "ETH"))
        .thenReturn(Optional.of(UUID.randomUUID()));

    TransactionResponse response =
        transactionService.updateTransaction(userId, txId, request, IDEMPOTENCY_KEY);

    assertThat(response.assetSymbol()).isEqualTo("ETH");
    assertThat(response.totalValue()).isEqualByComparingTo("4000.6250");
    verify(portfolioProjectionSyncPort).reconcileUserPortfolio(userId);
    verify(portfolioProjectionSyncPort).resolvePortfolioEntryId(userId, "ETH");
  }

  @Test
  @DisplayName("updateTransaction: throws on missing price for non-transfer")
  void updateTransactionRejectsMissingPriceForBuySell() {
    UUID txId = transaction.getTransactionId();
    UpdateTransactionRequest request =
        new UpdateTransactionRequest(
            "BTC",
            "CRYPTO",
            new BigDecimal("0.50"),
            null,
            OffsetDateTime.of(2026, 3, 12, 8, 45, 0, 0, ZoneOffset.UTC),
            BigDecimal.ZERO,
            "updated tx",
            null);

    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(transaction));

    assertThatThrownBy(
            () -> transactionService.updateTransaction(userId, txId, request, IDEMPOTENCY_KEY))
        .isInstanceOf(
            com.mx.cryptomonitor.transaction.domain.exception.InvalidTransactionException.class)
        .hasMessageContaining("precio por unidad");
  }

  @Test
  @DisplayName("updateTransaction: transfer keeps zero price and transfer type")
  void updateTransactionTransferUsesZeroPriceAndTotal() {
    UUID txId = transaction.getTransactionId();
    transaction.setTransactionType("TRANSFER");
    transaction.setPricePerUnit(BigDecimal.ZERO);
    transaction.setTotalValue(BigDecimal.ZERO);

    UpdateTransactionRequest request =
        new UpdateTransactionRequest(
            "btc",
            "CRYPTO",
            new BigDecimal("0.75"),
            null,
            OffsetDateTime.of(2026, 3, 12, 8, 45, 0, 0, ZoneOffset.UTC),
            new BigDecimal("0.0002"),
            "updated transfer",
            "transfer_out");

    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(transaction));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Transaction.class));
    when(portfolioProjectionSyncPort.resolvePortfolioEntryId(userId, "BTC"))
        .thenReturn(Optional.of(UUID.randomUUID()));

    TransactionResponse response =
        transactionService.updateTransaction(userId, txId, request, IDEMPOTENCY_KEY);

    assertThat(response.assetSymbol()).isEqualTo("BTC");
    assertThat(response.transactionType()).isEqualTo("TRANSFER");
    assertThat(response.pricePerUnit()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(transaction.getTransferType()).isEqualTo("TRANSFER_OUT");
  }

  @Test
  @DisplayName("updateTransaction: throws when transfer type is missing for transfer")
  void updateTransactionTransferRejectsMissingTransferType() {
    UUID txId = transaction.getTransactionId();
    transaction.setTransactionType("TRANSFER");

    UpdateTransactionRequest request =
        new UpdateTransactionRequest(
            "BTC",
            "CRYPTO",
            new BigDecimal("0.50"),
            null,
            OffsetDateTime.of(2026, 3, 12, 8, 45, 0, 0, ZoneOffset.UTC),
            BigDecimal.ZERO,
            "updated transfer",
            null);

    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(transaction));

    assertThatThrownBy(
            () -> transactionService.updateTransaction(userId, txId, request, IDEMPOTENCY_KEY))
        .isInstanceOf(
            com.mx.cryptomonitor.transaction.domain.exception.InvalidTransactionException.class)
        .hasMessageContaining("tipo de transferencia");

    verify(transactionRepository, never()).save(any(Transaction.class));
    verify(portfolioProjectionSyncPort, never()).reconcileUserPortfolio(any(UUID.class));
  }

  @Test
  @DisplayName("updateTransaction: throws when transaction does not exist")
  void updateTransactionNotFound() {
    UUID txId = UUID.randomUUID();
    UpdateTransactionRequest request =
        new UpdateTransactionRequest(
            "BTC",
            "CRYPTO",
            new BigDecimal("0.50"),
            new BigDecimal("95000.00"),
            OffsetDateTime.of(2026, 3, 12, 8, 45, 0, 0, ZoneOffset.UTC),
            BigDecimal.ZERO,
            "updated tx",
            null);

    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> transactionService.updateTransaction(userId, txId, request, IDEMPOTENCY_KEY))
        .isInstanceOf(TransactionNotFoundException.class);
  }

  @Test
  @DisplayName("updateTransaction: succeeds even when reconciled portfolio entry is absent")
  void updateTransactionAllowsMissingPortfolioEntryAfterReconcile() {
    UUID txId = transaction.getTransactionId();
    UpdateTransactionRequest request =
        new UpdateTransactionRequest(
            "ETH",
            "CRYPTO",
            new BigDecimal("1.00"),
            new BigDecimal("2500.00"),
            OffsetDateTime.of(2026, 3, 12, 8, 45, 0, 0, ZoneOffset.UTC),
            BigDecimal.ZERO,
            "updated tx",
            null);

    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.of(transaction));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Transaction.class));
    when(portfolioProjectionSyncPort.resolvePortfolioEntryId(userId, "ETH"))
        .thenReturn(Optional.empty());

    TransactionResponse response =
        transactionService.updateTransaction(userId, txId, request, IDEMPOTENCY_KEY);

    assertThat(response.assetSymbol()).isEqualTo("ETH");
    verify(portfolioProjectionSyncPort).reconcileUserPortfolio(userId);
  }

  @Test
  @DisplayName("deleteTransactionById: throws when not found")
  void deleteTransactionByIdNotFound() {
    UUID txId = UUID.randomUUID();
    when(transactionRepository.findByTransactionIdAndUserId(txId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> transactionService.deleteTransactionById(userId, txId, IDEMPOTENCY_KEY))
        .isInstanceOf(TransactionNotFoundException.class);
  }
}
