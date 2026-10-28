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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.transaction.application.dto.request.DividendTransactionRequest;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.application.service.DividendAccrualService;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendEntitlementCalculator;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxCalculator;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class DividendAccrualServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private DividendDetailRepository dividendDetailRepository;
  @Mock private DividendEntitlementCalculator entitlementCalculator;
  @Mock private TransactionCommandUseCase transactionCommand;
  @Mock private MarketZoneQueryPort marketZoneQuery;

  private DividendAccrualService service;

  private final UUID userA = UUID.randomUUID();
  private static final LocalDate EX_DATE = LocalDate.of(2024, 6, 10);
  private static final LocalDate PAY_DATE = LocalDate.of(2024, 6, 17);

  @BeforeEach
  void setUp() {
    service =
        new DividendAccrualService(
            transactionRepository,
            dividendDetailRepository,
            entitlementCalculator,
            new DividendTaxCalculator(),
            transactionCommand,
            marketZoneQuery);
  }

  @Test
  void createsOneDividendPerEntitledUserWithTaxChainAndNyOperationDate() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("GOOGL"))
        .thenReturn(List.of());
    when(entitlementCalculator.entitledQuantityByUser(any(), eq(EX_DATE)))
        .thenReturn(Map.of(userA, new BigDecimal("100")));
    when(dividendDetailRepository
            .existsByTransaction_User_IdAndTransaction_AssetSymbolAndExDividendDate(
                userA, "GOOGL", EX_DATE))
        .thenReturn(false);
    when(marketZoneQuery.zoneIdFor("USD", null)).thenReturn(Optional.of("America/New_York"));

    int created =
        service.accrue("googl", "STOCK", EX_DATE, PAY_DATE, new BigDecimal("0.20"), "USD", null);

    assertThat(created).isEqualTo(1);

    ArgumentCaptor<DividendTransactionRequest> captor =
        ArgumentCaptor.forClass(DividendTransactionRequest.class);
    verify(transactionCommand)
        .registerDividendTransaction(eq(userA), captor.capture(), any(String.class));
    DividendTransactionRequest request = captor.getValue();
    assertThat(request.assetSymbol()).isEqualTo("GOOGL");
    assertThat(request.amount()).isEqualByComparingTo("20"); // 100 x 0.20 bruto
    assertThat(request.taxWithheld()).isEqualByComparingTo("3.8"); // 2.00 US + 1.80 MX
    assertThat(request.exDividendDate()).isEqualTo(EX_DATE);
    assertThat(request.dividendType()).isEqualTo("CASH");
    // Payment date fijada al inicio del dia en la plaza (NY): 2024-06-17T00:00-04:00
    assertThat(request.transactionDate().toLocalDate()).isEqualTo(PAY_DATE);
    assertThat(request.transactionDate().getOffset().getTotalSeconds()).isEqualTo(-4 * 3600);
  }

  @Test
  void skipsUsersAlreadyAccrued() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("GOOGL"))
        .thenReturn(List.of());
    when(entitlementCalculator.entitledQuantityByUser(any(), eq(EX_DATE)))
        .thenReturn(Map.of(userA, new BigDecimal("100")));
    when(dividendDetailRepository
            .existsByTransaction_User_IdAndTransaction_AssetSymbolAndExDividendDate(
                userA, "GOOGL", EX_DATE))
        .thenReturn(true);

    int created =
        service.accrue("GOOGL", "STOCK", EX_DATE, PAY_DATE, new BigDecimal("0.20"), "USD", null);

    assertThat(created).isZero();
    verify(transactionCommand, never()).registerDividendTransaction(any(), any(), any());
  }

  @Test
  void noEntitledHoldersIsNoOp() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("GOOGL"))
        .thenReturn(List.of());
    when(entitlementCalculator.entitledQuantityByUser(any(), eq(EX_DATE))).thenReturn(Map.of());

    int created =
        service.accrue("GOOGL", "STOCK", EX_DATE, PAY_DATE, new BigDecimal("0.20"), "USD", null);

    assertThat(created).isZero();
    verify(transactionCommand, never()).registerDividendTransaction(any(), any(), any());
  }

  @Test
  void nonPositiveDividendPerShareIsRejected() {
    int created = service.accrue("GOOGL", "STOCK", EX_DATE, PAY_DATE, BigDecimal.ZERO, "USD", null);

    assertThat(created).isZero();
    verify(transactionRepository, never())
        .findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc(any());
  }
}
