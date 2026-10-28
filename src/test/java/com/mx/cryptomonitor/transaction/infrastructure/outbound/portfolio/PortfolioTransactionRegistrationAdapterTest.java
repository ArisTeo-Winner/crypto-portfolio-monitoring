package com.mx.cryptomonitor.transaction.infrastructure.outbound.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioEntryPort;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioTransactionRegistrationAdapterTest {

  private final Logger logger =
      LoggerFactory.getLogger(PortfolioTransactionRegistrationAdapterTest.class);

  @Mock private UserRepository userRepository;
  @Mock private PortfolioEntryPort portfolioEntryPort;
  @Mock private TransactionRepository transactionRepository;
  @Mock private TransactionMapper transactionMapper;
  @Mock private MarketZoneQueryPort marketZoneQuery;

  @InjectMocks private PortfolioTransactionRegistrationAdapter adapter;

  @Captor private ArgumentCaptor<Transaction> transactionCaptor;

  @Test
  void registerTransactionShouldPersistPortfolioEntryIdReturnedByPortfolioPort() {
    UUID userId = UUID.randomUUID();
    UUID portfolioEntryId = UUID.randomUUID();

    User user = new User();
    user.setId(userId);
    user.setUsername("tester");
    user.setEmail("tester@example.com");

    TransactionRequest request =
        new TransactionRequest(
            "BTC",
            AssetType.CRYPTO,
            "BUY",
            new BigDecimal("0.25"),
            new BigDecimal("95000.00"),
            new BigDecimal("23750.00"),
            OffsetDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC),
            new BigDecimal("10.00"),
            "adapter regression check",
            null);

    logger.info("List TransactionRequest: {}", request);

    Transaction mapped = new Transaction();
    TransactionResponse response =
        new TransactionResponse(
            UUID.randomUUID(),
            "BTC",
            "CRYPTO",
            "BUY",
            new BigDecimal("0.25"),
            new BigDecimal("95000.00"),
            new BigDecimal("23750.00"),
            OffsetDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC),
            null,
            null,
            null,
            new BigDecimal("10.00"),
            "adapter regression check",
            OffsetDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(portfolioEntryPort.applyTransaction(any(UUID.class), any())).thenReturn(portfolioEntryId);
    when(transactionMapper.toEntity(request)).thenReturn(mapped);
    when(transactionMapper.toResponse(mapped)).thenReturn(response);

    TransactionResponse result = adapter.registerTransaction(userId, request);

    verify(transactionRepository).save(transactionCaptor.capture());
    Transaction saved = transactionCaptor.getValue();

    assertThat(saved.getPortfolioEntryId()).isEqualTo(portfolioEntryId);
    assertThat(saved.getUser()).isSameAs(user);
    assertThat(result.assetSymbol()).isEqualTo("BTC");

    // ADR-0013: crypto -> binomio en UTC, sin consultar el mapeo de mercado.
    assertThat(saved.getExecutionZoneId()).isEqualTo("UTC");
    assertThat(saved.getLocalExecutionTime()).isEqualTo(LocalDateTime.of(2026, 3, 11, 12, 0));
    assertThat(saved.getExecutedAtUtc()).isEqualTo(OffsetDateTime.parse("2026-03-11T12:00:00Z"));
  }

  @Test
  void registerStockTransactionResolvesMarketZoneAndBuildsBinomial() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);

    TransactionRequest request =
        new TransactionRequest(
            "AAPL",
            AssetType.STOCK,
            "BUY",
            new BigDecimal("1"),
            new BigDecimal("200.00"),
            new BigDecimal("200.00"),
            OffsetDateTime.parse("2026-01-15T15:00:00Z"),
            BigDecimal.ZERO,
            "stock binomio",
            null,
            "Apple Inc",
            "NASDAQ",
            "GBM",
            "USD",
            null,
            null,
            null,
            Boolean.FALSE);

    Transaction mapped = new Transaction();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(portfolioEntryPort.applyTransaction(any(UUID.class), any())).thenReturn(UUID.randomUUID());
    when(transactionMapper.toEntity(request)).thenReturn(mapped);
    when(transactionMapper.toResponse(mapped)).thenReturn(null);
    when(marketZoneQuery.zoneIdFor("USD", "NASDAQ")).thenReturn(Optional.of("America/New_York"));

    adapter.registerTransaction(userId, request);

    verify(transactionRepository).save(transactionCaptor.capture());
    Transaction saved = transactionCaptor.getValue();

    // 15-ene: NY en EST (UTC-5) -> 15:00Z = 10:00 local; executed_at_utc recupera el instante.
    assertThat(saved.getExecutionZoneId()).isEqualTo("America/New_York");
    assertThat(saved.getLocalExecutionTime()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 0));
    assertThat(saved.getExecutedAtUtc()).isEqualTo(OffsetDateTime.parse("2026-01-15T15:00:00Z"));
  }
}
