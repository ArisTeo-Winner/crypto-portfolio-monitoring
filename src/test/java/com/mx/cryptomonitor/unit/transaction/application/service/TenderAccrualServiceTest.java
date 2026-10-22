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
import com.mx.cryptomonitor.transaction.application.service.TenderAccrualService;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;

@ExtendWith(MockitoExtension.class)
class TenderAccrualServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private SecurityPositionPort securityPositionPort;
  @Mock private TransactionCommandUseCase transactionCommand;
  @Mock private SecurityStatusPort securityStatusPort;
  @Mock private MarketZoneQueryPort marketZoneQuery;

  private TenderAccrualService service;

  private final UUID userA = UUID.randomUUID();
  private static final LocalDate OFFER_DATE = LocalDate.of(2024, 9, 15);

  @BeforeEach
  void setUp() {
    service =
        new TenderAccrualService(
            transactionRepository,
            securityPositionPort,
            transactionCommand,
            securityStatusPort,
            marketZoneQuery);
  }

  private void holderOfTraxion() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("TRAXIONA"))
        .thenReturn(
            List.of(
                Transaction.builder()
                    .user(User.builder().id(userA).build())
                    .assetSymbol("TRAXIONA")
                    .build()));
    when(marketZoneQuery.zoneIdFor("MXN", null)).thenReturn(Optional.of("America/Mexico_City"));
  }

  @Test
  void sellsFullPositionAtOfferPriceAndMarksDelisted() {
    holderOfTraxion();
    when(securityPositionPort.positionOf(userA, "TRAXIONA"))
        .thenReturn(
            Optional.of(new SecurityPosition(new BigDecimal("500"), new BigDecimal("9000"))));

    int created = service.accrue("traxiona", new BigDecimal("28.5"), "MXN", OFFER_DATE, true);

    assertThat(created).isEqualTo(1);

    ArgumentCaptor<SellTransactionRequest> sellCaptor =
        ArgumentCaptor.forClass(SellTransactionRequest.class);
    verify(transactionCommand)
        .registerSellTransaction(eq(userA), sellCaptor.capture(), any(String.class));
    assertThat(sellCaptor.getValue().assetSymbol()).isEqualTo("TRAXIONA");
    assertThat(sellCaptor.getValue().quantity()).isEqualByComparingTo("500");
    assertThat(sellCaptor.getValue().pricePerUnit()).isEqualByComparingTo("28.5");

    verify(securityStatusPort).markDelisted("TRAXIONA");
  }

  @Test
  void doesNotMarkDelistedWhenOfferDidNotDelist() {
    holderOfTraxion();
    when(securityPositionPort.positionOf(userA, "TRAXIONA"))
        .thenReturn(
            Optional.of(new SecurityPosition(new BigDecimal("500"), new BigDecimal("9000"))));

    service.accrue("TRAXIONA", new BigDecimal("28.5"), "MXN", OFFER_DATE, false);

    verify(transactionCommand).registerSellTransaction(eq(userA), any(), any(String.class));
    verify(securityStatusPort, never()).markDelisted(any());
  }

  @Test
  void noHoldersDoesNotMarkDelisted() {
    when(transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc("TRAXIONA"))
        .thenReturn(List.of());

    int created = service.accrue("TRAXIONA", new BigDecimal("28.5"), "MXN", OFFER_DATE, true);

    assertThat(created).isZero();
    verify(securityStatusPort, never()).markDelisted(any());
  }

  @Test
  void nonPositiveOfferPriceIsNoOp() {
    assertThat(service.accrue("TRAXIONA", BigDecimal.ZERO, "MXN", OFFER_DATE, true)).isZero();
    verify(transactionRepository, never())
        .findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc(any());
  }
}
