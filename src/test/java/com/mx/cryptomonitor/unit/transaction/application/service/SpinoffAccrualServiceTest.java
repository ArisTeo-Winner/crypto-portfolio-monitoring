package com.mx.cryptomonitor.unit.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort.SecurityPosition;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.application.service.SpinoffAccrualService;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;

@ExtendWith(MockitoExtension.class)
class SpinoffAccrualServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private SecurityPositionPort securityPositionPort;
  @Mock private TransactionCommandUseCase transactionCommand;
  @Mock private MarketZoneQueryPort marketZoneQuery;

  private SpinoffAccrualService service;

  private final UUID userA = UUID.randomUUID();
  private static final LocalDate EFFECTIVE = LocalDate.of(2024, 7, 1);

  @BeforeEach
  void setUp() {
    service =
        new SpinoffAccrualService(
            transactionRepository, securityPositionPort, transactionCommand, marketZoneQuery);
  }

  @Test
  void rebasesParentAndDistributesSpincoWithAllocatedBasis() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("PARENT"))
        .thenReturn(
            List.of(
                Transaction.builder()
                    .user(User.builder().id(userA).build())
                    .assetSymbol("PARENT")
                    .build()));
    when(marketZoneQuery.zoneIdFor("USD", null)).thenReturn(Optional.of("America/New_York"));
    when(securityPositionPort.positionOf(userA, "PARENT"))
        .thenReturn(
            Optional.of(new SecurityPosition(new BigDecimal("100"), new BigDecimal("1000"))));

    int created =
        service.accrue(
            "parent", "spinco", new BigDecimal("0.5"), new BigDecimal("0.20"), "USD", EFFECTIVE);

    assertThat(created).isEqualTo(1);

    // 1) TRANSFER_OUT de la matriz (baja basis-neutral).
    ArgumentCaptor<TransferTransactionRequest> outCaptor =
        ArgumentCaptor.forClass(TransferTransactionRequest.class);
    verify(transactionCommand)
        .registerTransferTransaction(eq(userA), outCaptor.capture(), any(String.class));
    assertThat(outCaptor.getValue().assetSymbol()).isEqualTo("PARENT");
    assertThat(outCaptor.getValue().transferType()).isEqualTo("TRANSFER_OUT");
    assertThat(outCaptor.getValue().quantity()).isEqualByComparingTo("100");

    // 2) + 3) BUY re-base de la matriz (800/100=8) y BUY de la spinco (200/50=4).
    ArgumentCaptor<BuyTransactionRequest> buyCaptor =
        ArgumentCaptor.forClass(BuyTransactionRequest.class);
    verify(transactionCommand, org.mockito.Mockito.times(2))
        .registerBuyTransaction(eq(userA), buyCaptor.capture(), any(String.class));

    BuyTransactionRequest parentBuy =
        buyCaptor.getAllValues().stream()
            .filter(b -> "PARENT".equals(b.assetSymbol()))
            .findFirst()
            .orElseThrow();
    assertThat(parentBuy.quantity()).isEqualByComparingTo("100");
    assertThat(parentBuy.pricePerUnit()).isEqualByComparingTo("8"); // 800 / 100

    BuyTransactionRequest spincoBuy =
        buyCaptor.getAllValues().stream()
            .filter(b -> "SPINCO".equals(b.assetSymbol()))
            .findFirst()
            .orElseThrow();
    assertThat(spincoBuy.quantity()).isEqualByComparingTo("50"); // 100 * 0.5
    assertThat(spincoBuy.pricePerUnit()).isEqualByComparingTo("4"); // 200 / 50
  }

  @Test
  void blankSpincoIsNoOp() {
    assertThat(
            service.accrue("PARENT", "  ", BigDecimal.ONE, new BigDecimal("0.2"), "USD", EFFECTIVE))
        .isZero();
    verify(transactionRepository, org.mockito.Mockito.never())
        .findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc(any());
  }
}
