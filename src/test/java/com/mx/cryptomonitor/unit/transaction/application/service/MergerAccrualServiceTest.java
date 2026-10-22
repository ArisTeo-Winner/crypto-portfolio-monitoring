package com.mx.cryptomonitor.unit.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.asset.application.port.in.SecuritySuccessionPort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort.SecurityPosition;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.application.service.MergerAccrualService;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;

@ExtendWith(MockitoExtension.class)
class MergerAccrualServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private SecurityPositionPort securityPositionPort;
  @Mock private TransactionCommandUseCase transactionCommand;
  @Mock private SecuritySuccessionPort securitySuccessionPort;
  @Mock private MarketZoneQueryPort marketZoneQuery;

  private MergerAccrualService service;

  private final UUID userA = UUID.randomUUID();
  private static final LocalDate EFFECTIVE = LocalDate.of(2024, 3, 1);

  @BeforeEach
  void setUp() {
    service =
        new MergerAccrualService(
            transactionRepository,
            securityPositionPort,
            transactionCommand,
            securitySuccessionPort,
            marketZoneQuery);
  }

  private void holderOfCept() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("CEPT"))
        .thenReturn(
            List.of(
                Transaction.builder()
                    .user(User.builder().id(userA).build())
                    .assetSymbol("CEPT")
                    .build()));
    when(marketZoneQuery.zoneIdFor("USD", null)).thenReturn(Optional.of("America/New_York"));
  }

  @Test
  void allStockTransfersOutPredecessorAndBuysSuccessorAtCarriedBasis() {
    holderOfCept();
    when(securityPositionPort.positionOf(userA, "CEPT"))
        .thenReturn(
            Optional.of(new SecurityPosition(new BigDecimal("100"), new BigDecimal("1500"))));

    int created = service.accrue("cept", "secz", EFFECTIVE, new BigDecimal("1.5"), null, "USD");

    assertThat(created).isEqualTo(1);

    ArgumentCaptor<TransferTransactionRequest> outCaptor =
        ArgumentCaptor.forClass(TransferTransactionRequest.class);
    verify(transactionCommand)
        .registerTransferTransaction(eq(userA), outCaptor.capture(), any(String.class));
    assertThat(outCaptor.getValue().assetSymbol()).isEqualTo("CEPT");
    assertThat(outCaptor.getValue().transferType()).isEqualTo("TRANSFER_OUT");
    assertThat(outCaptor.getValue().quantity()).isEqualByComparingTo("100");

    ArgumentCaptor<BuyTransactionRequest> inCaptor =
        ArgumentCaptor.forClass(BuyTransactionRequest.class);
    verify(transactionCommand)
        .registerBuyTransaction(eq(userA), inCaptor.capture(), any(String.class));
    assertThat(inCaptor.getValue().assetSymbol()).isEqualTo("SECZ");
    assertThat(inCaptor.getValue().quantity()).isEqualByComparingTo("150"); // 100 * 1.5
    assertThat(inCaptor.getValue().pricePerUnit()).isEqualByComparingTo("10"); // 1500 / 150

    verify(transactionCommand, never()).registerSellTransaction(any(), any(), any());
    verify(securitySuccessionPort).recordMerger("CEPT", "SECZ");
  }

  @Test
  void allCashSellsPredecessorAtCashPerShare() {
    holderOfCept();
    when(securityPositionPort.positionOf(userA, "CEPT"))
        .thenReturn(
            Optional.of(new SecurityPosition(new BigDecimal("100"), new BigDecimal("1500"))));

    int created = service.accrue("CEPT", null, EFFECTIVE, null, new BigDecimal("20"), "USD");

    assertThat(created).isEqualTo(1);

    ArgumentCaptor<SellTransactionRequest> sellCaptor =
        ArgumentCaptor.forClass(SellTransactionRequest.class);
    verify(transactionCommand)
        .registerSellTransaction(eq(userA), sellCaptor.capture(), any(String.class));
    assertThat(sellCaptor.getValue().assetSymbol()).isEqualTo("CEPT");
    assertThat(sellCaptor.getValue().quantity()).isEqualByComparingTo("100");
    assertThat(sellCaptor.getValue().pricePerUnit()).isEqualByComparingTo("20");

    verify(transactionCommand, never()).registerTransferTransaction(any(), any(), any());
    verify(transactionCommand, never()).registerBuyTransaction(any(), any(), any());
    verify(securitySuccessionPort).recordMerger("CEPT", null);
  }

  @Test
  void skipsHolderWithoutPosition() {
    holderOfCept();
    when(securityPositionPort.positionOf(userA, "CEPT")).thenReturn(Optional.empty());

    int created = service.accrue("CEPT", "SECZ", EFFECTIVE, new BigDecimal("1"), null, "USD");

    assertThat(created).isZero();
    verify(transactionCommand, never()).registerTransferTransaction(any(), any(), any());
    verify(securitySuccessionPort, never()).recordMerger(any(), any());
  }

  @Test
  void blankPredecessorIsNoOp() {
    assertThat(service.accrue("  ", "SECZ", EFFECTIVE, BigDecimal.ONE, null, "USD")).isZero();
    verify(transactionRepository, never())
        .findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc(any());
  }
}
