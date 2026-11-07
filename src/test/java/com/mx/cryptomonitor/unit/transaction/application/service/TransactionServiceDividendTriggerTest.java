package com.mx.cryptomonitor.unit.transaction.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogRefreshPort;
import com.mx.cryptomonitor.shared.event.TransactionRegisteredEvent;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.application.port.out.PortfolioProjectionSyncPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionAuditPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionRegistrationPort;
import com.mx.cryptomonitor.transaction.application.service.TransactionIdempotencyService;
import com.mx.cryptomonitor.transaction.application.service.TransactionRealizedPnlService;
import com.mx.cryptomonitor.transaction.application.service.TransactionService;
import com.mx.cryptomonitor.transaction.domain.friction.GbmFrictionCalculator;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

/**
 * ADR-0024: la compra de un activo con dividendos publica el evento de auto-poblado; el resto no.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceDividendTriggerTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private TransactionRegistrationPort transactionRegistrationPort;
  @Mock private PortfolioProjectionSyncPort portfolioProjectionSyncPort;
  @Mock private TransactionMapper transactionMapper;
  @Mock private TransactionIdempotencyService transactionIdempotencyService;
  @Mock private TransactionAuditPort transactionAuditPort;
  @Mock private TransactionRealizedPnlService transactionRealizedPnlService;
  @Mock private DividendDetailRepository dividendDetailRepository;
  @Mock private AssetCatalogQueryPort assetCatalogQueryPort;
  @Mock private GbmFrictionCalculator frictionCalculator;
  @Mock private AssetCatalogRefreshPort assetCatalogRefreshPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private TransactionService service;

  private static final UUID USER_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
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
            frictionCalculator,
            assetCatalogRefreshPort,
            eventPublisher);

    // Ejecuta el supplier real del registro (lo que haría la transacción de idempotencia).
    when(transactionIdempotencyService.executeForTransactionResponse(
            eq(USER_ID), anyString(), anyString(), any(), any()))
        .thenAnswer(inv -> ((Supplier<TransactionResponse>) inv.getArgument(4)).get());
  }

  @Test
  void buyingAStockPublishesTheDividendSyncTrigger() {
    when(transactionRegistrationPort.registerTransaction(eq(USER_ID), any()))
        .thenReturn(response("GOOGL", "STOCK", "BUY"));

    service.registerBuyTransaction(USER_ID, buyRequest("GOOGL", "STOCK"), "idem-key");

    ArgumentCaptor<TransactionRegisteredEvent> captor =
        ArgumentCaptor.forClass(TransactionRegisteredEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().assetSymbol()).isEqualTo("GOOGL");
    org.assertj.core.api.Assertions.assertThat(captor.getValue().assetType()).isEqualTo("STOCK");
  }

  @Test
  void buyingCryptoDoesNotPublishTheDividendSyncTrigger() {
    when(transactionRegistrationPort.registerTransaction(eq(USER_ID), any()))
        .thenReturn(response("BTC", "CRYPTO", "BUY"));

    service.registerBuyTransaction(USER_ID, buyRequest("BTC", "CRYPTO"), "idem-key");

    verify(eventPublisher, never()).publishEvent(any(TransactionRegisteredEvent.class));
  }

  private BuyTransactionRequest buyRequest(String symbol, String assetType) {
    return new BuyTransactionRequest(
        symbol,
        assetType,
        BigDecimal.ONE,
        new BigDecimal("132.00"),
        null,
        OffsetDateTime.now(),
        null,
        "Alphabet",
        "NASDAQ",
        "GBM",
        "USD",
        null,
        null,
        null,
        Boolean.FALSE);
  }

  private TransactionResponse response(String symbol, String assetType, String txType) {
    return new TransactionResponse(
        UUID.randomUUID(),
        symbol,
        assetType,
        txType,
        BigDecimal.ONE,
        new BigDecimal("132.00"),
        new BigDecimal("132.00"),
        OffsetDateTime.now(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "Alphabet",
        "NASDAQ",
        "GBM",
        "USD",
        null,
        null,
        null,
        Boolean.FALSE);
  }
}
