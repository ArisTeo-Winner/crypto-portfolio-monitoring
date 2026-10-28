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

import com.mx.cryptomonitor.asset.application.port.in.SecurityStatusPort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort.SecurityPosition;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.application.service.BankruptcyAccrualService;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;

@ExtendWith(MockitoExtension.class)
class BankruptcyAccrualServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private SecurityPositionPort securityPositionPort;
  @Mock private TransactionCommandUseCase transactionCommand;
  @Mock private SecurityStatusPort securityStatusPort;
  @Mock private MarketZoneQueryPort marketZoneQuery;

  private BankruptcyAccrualService service;

  private final UUID userA = UUID.randomUUID();
  private static final LocalDate EFFECTIVE = LocalDate.of(2024, 5, 20);

  @BeforeEach
  void setUp() {
    service =
        new BankruptcyAccrualService(
            transactionRepository,
            securityPositionPort,
            transactionCommand,
            securityStatusPort,
            marketZoneQuery);
  }

  private void holder() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("DEFUNCT"))
        .thenReturn(
            List.of(
                Transaction.builder()
                    .user(User.builder().id(userA).build())
                    .assetSymbol("DEFUNCT")
                    .build()));
    when(marketZoneQuery.zoneIdFor("USD", null)).thenReturn(Optional.of("America/New_York"));
    when(securityPositionPort.positionOf(userA, "DEFUNCT"))
        .thenReturn(
            Optional.of(new SecurityPosition(new BigDecimal("300"), new BigDecimal("4500"))));
  }

  @Test
  void zeroRecoveryWritesOffAtPriceZeroAndMarksBankrupt() {
    holder();

    int created = service.accrue("defunct", BigDecimal.ZERO, "USD", EFFECTIVE);

    assertThat(created).isEqualTo(1);

    ArgumentCaptor<SellTransactionRequest> sellCaptor =
        ArgumentCaptor.forClass(SellTransactionRequest.class);
    verify(transactionCommand)
        .registerSellTransaction(eq(userA), sellCaptor.capture(), any(String.class));
    assertThat(sellCaptor.getValue().assetSymbol()).isEqualTo("DEFUNCT");
    assertThat(sellCaptor.getValue().quantity()).isEqualByComparingTo("300");
    assertThat(sellCaptor.getValue().pricePerUnit()).isEqualByComparingTo("0"); // write-off total

    verify(securityStatusPort).markBankrupt("DEFUNCT");
  }

  @Test
  void partialRecoverySellsAtRecoveryPrice() {
    holder();

    service.accrue("DEFUNCT", new BigDecimal("0.50"), "USD", EFFECTIVE);

    ArgumentCaptor<SellTransactionRequest> sellCaptor =
        ArgumentCaptor.forClass(SellTransactionRequest.class);
    verify(transactionCommand)
        .registerSellTransaction(eq(userA), sellCaptor.capture(), any(String.class));
    assertThat(sellCaptor.getValue().pricePerUnit()).isEqualByComparingTo("0.50");
    verify(securityStatusPort).markBankrupt("DEFUNCT");
  }

  @Test
  void noHoldersDoesNotMarkBankrupt() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("DEFUNCT"))
        .thenReturn(List.of());

    int created = service.accrue("DEFUNCT", BigDecimal.ZERO, "USD", EFFECTIVE);

    assertThat(created).isZero();
    verify(securityStatusPort, never()).markBankrupt(any());
  }

  @Test
  void negativeRecoveryIsNoOp() {
    assertThat(service.accrue("DEFUNCT", new BigDecimal("-1"), "USD", EFFECTIVE)).isZero();
    verify(transactionRepository, never())
        .findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc(any());
  }
}
