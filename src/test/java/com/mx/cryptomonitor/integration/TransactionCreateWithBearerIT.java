package com.mx.cryptomonitor.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest(
    properties = {
      "spring.main.lazy-initialization=true",
      "jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "jwt.access-token-expiration=3600000",
      "jwt.refresh-token-expiration=604800000"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionCreateWithBearerIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private PortfolioEntryRepository portfolioEntryRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private RefreshTokenStoreService refreshTokenStoreService;
  @MockBean private MarketDataProvider marketDataProvider;
  @MockBean private AssetPricePort assetPricePort;

  @BeforeEach
  void setUpMocks() {
    when(refreshTokenStoreService.store(
            anyString(), any(UUID.class), any(UUID.class), any(LocalDateTime.class), any(), any()))
        .thenAnswer(
            invocation ->
                new RefreshTokenStoreService.StoredRefreshToken(
                    UUID.randomUUID(),
                    invocation.getArgument(1, UUID.class),
                    invocation.getArgument(2, UUID.class),
                    false));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("95000.00")));
    when(assetPricePort.getCryptoPriceAmount("ETH"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("2800.00")));
    when(assetPricePort.getCryptoPriceAmount("BNB"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("757.00")));
    when(assetPricePort.getCryptoPriceAmount("SOL"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("125.10")));
    when(marketDataProvider.getLatest("AAPL"))
        .thenReturn(java.util.Optional.of(new BigDecimal("249.56")));
  }

  @Test
  void postMeTransactionsShouldPersistTransactionAndPortfolioEntry_whenBearerTokenIsValid()
      throws Exception {
    User user = persistRoleUser("tx-real-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransactionRequest()))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.assetSymbol").value("BTC"))
        .andExpect(jsonPath("$.transactionType").value("BUY"));

    Transaction savedTransaction =
        transactionRepository.findAll().stream()
            .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Transaction was not persisted"));

    assertThat(savedTransaction.getTransactionId()).isNotNull();
    assertThat(savedTransaction.getPortfolioEntryId()).isNotNull();
    assertThat(portfolioEntryRepository.findById(savedTransaction.getPortfolioEntryId()))
        .isPresent();
    assertThat(portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), "BTC"))
        .isPresent();
  }

  @Test
  void postBuyEndpointShouldPersistTransaction_whenBearerTokenIsValid() throws Exception {
    User user = persistRoleUser("tx-buy-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequest()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionId").isNotEmpty())
        .andExpect(jsonPath("$.transactionType").value("BUY"))
        .andExpect(jsonPath("$.assetSymbol").value("BTC"));

    assertThat(portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), "BTC"))
        .isPresent();
  }

  @Test
  void postBuyEndpointShouldPersistCryptoTransactionUsingCryptoPricePortWhenBearerTokenIsValid()
      throws Exception {
    User user = persistRoleUser("tx-buy-missing-price-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequestForBnb()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("BUY"))
        .andExpect(jsonPath("$.assetSymbol").value("BNB"));

    assertThat(portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), "BNB"))
        .isPresent();
    assertThat(
            transactionRepository.findAll().stream()
                .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
                .filter(transaction -> "BNB".equals(transaction.getAssetSymbol())))
        .hasSize(1);
  }

  @Test
  void postBuyEndpointShouldPersistStockTransactionWithStockAssetType() throws Exception {
    User user = persistRoleUser("tx-buy-stock-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequestForAapl()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("BUY"))
        .andExpect(jsonPath("$.assetSymbol").value("AAPL"))
        .andExpect(jsonPath("$.assetType").value("STOCK"));

    assertThat(portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), "AAPL"))
        .hasValueSatisfying(entry -> assertThat(entry.getAssetType()).isEqualTo("STOCK"));
    assertThat(
            transactionRepository.findAll().stream()
                .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
                .filter(transaction -> "AAPL".equals(transaction.getAssetSymbol())))
        .anySatisfy(
            transaction -> assertThat(transaction.getAssetType()).isEqualTo(AssetType.STOCK));
  }

  @Test
  void postTransferEndpointShouldPersistTransferTypeAndExposeDetails() throws Exception {
    User user = persistRoleUser("tx-transfer-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/transfer")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferTransactionRequest()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("TRANSFER"));

    Transaction savedTransaction =
        transactionRepository.findAll().stream()
            .filter(
                transaction ->
                    transaction.getUser().getId().equals(user.getId())
                        && "TRANSFER".equals(transaction.getTransactionType()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Transfer transaction was not persisted"));

    assertThat(savedTransaction.getTransferType()).isEqualTo("TRANSFER_IN");
    assertThat(savedTransaction.getPortfolioEntryId()).isNotNull();

    mockMvc
        .perform(
            get(
                    "/api/v1/me/transactions/details/{transactionId}",
                    savedTransaction.getTransactionId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionType").value("TRANSFER"))
        .andExpect(jsonPath("$.transferType").value("TRANSFER_IN"))
        .andExpect(jsonPath("$.amountLabel").doesNotExist());
  }

  @Test
  void getUserTransactionsShouldExposeTransactionIdForDeleteFlow() throws Exception {
    User user = persistRoleUser("tx-list-id-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequestForSol()))
        .andExpect(status().isCreated());

    Transaction savedTransaction =
        transactionRepository.findAll().stream()
            .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
            .filter(transaction -> "SOL".equals(transaction.getAssetSymbol()))
            .max(Comparator.comparing(Transaction::getCreatedAt))
            .orElseThrow(() -> new AssertionError("SOL transaction was not persisted"));

    mockMvc
        .perform(
            get("/api/v1/me/transactions")
                .param("assetSymbol", "SOL")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(
            jsonPath("$[0].transactionId").value(savedTransaction.getTransactionId().toString()))
        .andExpect(jsonPath("$[0].assetSymbol").value("SOL"));
  }

  @Test
  void getUserTransactionsShouldReturnMostRecentTransactionsFirst() throws Exception {
    User user = persistRoleUser("tx-order-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    validBuyTransactionRequestForEthAt(
                        "0.4", "2100.00", "2026-03-18T09:15:00", "oldest eth buy")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    validBuyTransactionRequestForEthAt(
                        "0.7", "2200.00", "2026-03-20T11:45:00", "middle eth buy")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    validBuyTransactionRequestForEthAt(
                        "1.0", "2300.00", "2026-03-22T16:30:00", "newest eth buy")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/me/transactions")
                .param("assetSymbol", "ETH")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
        .andExpect(jsonPath("$[0].notes").value("newest eth buy"))
        .andExpect(jsonPath("$[0].transactionDate").value("2026-03-22T16:30:00"))
        .andExpect(jsonPath("$[1].notes").value("middle eth buy"))
        .andExpect(jsonPath("$[1].transactionDate").value("2026-03-20T11:45:00"))
        .andExpect(jsonPath("$[2].notes").value("oldest eth buy"))
        .andExpect(jsonPath("$[2].transactionDate").value("2026-03-18T09:15:00"));
  }

  @Test
  void putTransactionShouldUpdateStoredValuesAndReconcilePortfolioProjection() throws Exception {
    User user = persistRoleUser("tx-update-btc-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequest()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.assetSymbol").value("BTC"));

    Transaction savedTransaction =
        transactionRepository.findAll().stream()
            .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
            .filter(transaction -> "BTC".equals(transaction.getAssetSymbol()))
            .max(Comparator.comparing(Transaction::getCreatedAt))
            .orElseThrow(() -> new AssertionError("BTC transaction was not persisted"));

    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", savedTransaction.getTransactionId())
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateTransactionRequestForEth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assetSymbol").value("ETH"))
        .andExpect(jsonPath("$.quantity").value(1.25))
        .andExpect(jsonPath("$.totalValue").value(3500.00));

    assertThat(
            transactionRepository.findByTransactionIdAndUserId(
                savedTransaction.getTransactionId(), user.getId()))
        .hasValueSatisfying(
            transaction -> {
              assertThat(transaction.getAssetSymbol()).isEqualTo("ETH");
              assertThat(transaction.getAssetType()).isEqualTo(AssetType.CRYPTO);
              assertThat(transaction.getQuantity()).isEqualByComparingTo("1.25");
              assertThat(transaction.getPricePerUnit()).isEqualByComparingTo("2800.00");
              assertThat(transaction.getTotalValue()).isEqualByComparingTo("3500.0000");
            });

    mockMvc
        .perform(get("/api/v1/me/portfolio").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].assetSymbol").value(org.hamcrest.Matchers.hasItem("ETH")))
        .andExpect(
            jsonPath("$[*].assetSymbol")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("BTC"))));

    assertThat(portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), "ETH"))
        .isPresent();
    assertThat(portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), "BTC")).isEmpty();
  }

  @Test
  void putTransactionShouldPreserveTransferSemanticsAndExposeUpdatedDetails() throws Exception {
    User user = persistRoleUser("tx-update-transfer-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/transfer")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferTransactionRequest()))
        .andExpect(status().isCreated());

    Transaction savedTransaction =
        transactionRepository.findAll().stream()
            .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
            .filter(transaction -> "TRANSFER".equals(transaction.getTransactionType()))
            .max(Comparator.comparing(Transaction::getCreatedAt))
            .orElseThrow(() -> new AssertionError("TRANSFER transaction was not persisted"));

    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", savedTransaction.getTransactionId())
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateTransferTransactionRequest()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionType").value("TRANSFER"))
        .andExpect(jsonPath("$.assetSymbol").value("ETH"))
        .andExpect(jsonPath("$.pricePerUnit").value(0));

    assertThat(
            transactionRepository.findByTransactionIdAndUserId(
                savedTransaction.getTransactionId(), user.getId()))
        .hasValueSatisfying(
            transaction -> {
              assertThat(transaction.getAssetSymbol()).isEqualTo("ETH");
              assertThat(transaction.getTransferType()).isEqualTo("TRANSFER_IN");
              assertThat(transaction.getPricePerUnit()).isEqualByComparingTo(BigDecimal.ZERO);
              assertThat(transaction.getTotalValue()).isEqualByComparingTo(BigDecimal.ZERO);
            });

    mockMvc
        .perform(
            get(
                    "/api/v1/me/transactions/details/{transactionId}",
                    savedTransaction.getTransactionId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assetSymbol").value("ETH"))
        .andExpect(jsonPath("$.transferType").value("TRANSFER_IN"))
        .andExpect(jsonPath("$.amountLabel").doesNotExist());
  }

  @Test
  void putTransactionShouldReturn404WhenTryingToEditAnotherUsersTransaction() throws Exception {
    User owner = persistRoleUser("tx-update-owner-" + UUID.randomUUID() + "@example.com");
    User intruder = persistRoleUser("tx-update-intruder-" + UUID.randomUUID() + "@example.com");
    String ownerAccessToken = loginAndGetAccessToken(owner.getEmail(), "ValidPass123!");
    String intruderAccessToken = loginAndGetAccessToken(intruder.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + ownerAccessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequestForAapl()))
        .andExpect(status().isCreated());

    Transaction ownersTransaction =
        transactionRepository.findAll().stream()
            .filter(transaction -> transaction.getUser().getId().equals(owner.getId()))
            .filter(transaction -> "AAPL".equals(transaction.getAssetSymbol()))
            .max(Comparator.comparing(Transaction::getCreatedAt))
            .orElseThrow(() -> new AssertionError("AAPL transaction was not persisted"));

    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", ownersTransaction.getTransactionId())
                .header("Authorization", "Bearer " + intruderAccessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateTransactionRequestForMsft()))
        .andExpect(status().isNotFound());

    assertThat(
            transactionRepository.findByTransactionIdAndUserId(
                ownersTransaction.getTransactionId(), owner.getId()))
        .hasValueSatisfying(
            transaction -> {
              assertThat(transaction.getAssetSymbol()).isEqualTo("AAPL");
              assertThat(transaction.getAssetType()).isEqualTo(AssetType.STOCK);
            });
  }

  @Test
  void putTransactionShouldReturn400WhenTransferIsEditedWithoutTransferType() throws Exception {
    User user = persistRoleUser("tx-update-transfer-invalid-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/transfer")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferTransactionRequest()))
        .andExpect(status().isCreated());

    Transaction savedTransaction =
        transactionRepository.findAll().stream()
            .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
            .filter(transaction -> "TRANSFER".equals(transaction.getTransactionType()))
            .max(Comparator.comparing(Transaction::getCreatedAt))
            .orElseThrow(() -> new AssertionError("TRANSFER transaction was not persisted"));

    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", savedTransaction.getTransactionId())
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidUpdateTransferTransactionRequest()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid Transaction"));
  }

  @Test
  void deleteTransactionShouldRemovePortfolioProjectionWhenLastAssetTransactionIsDeleted()
      throws Exception {
    User user = persistRoleUser("tx-delete-sol-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequestForSol()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.assetSymbol").value("SOL"));

    Transaction savedTransaction =
        transactionRepository.findAll().stream()
            .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
            .filter(transaction -> "SOL".equals(transaction.getAssetSymbol()))
            .max(Comparator.comparing(Transaction::getCreatedAt))
            .orElseThrow(() -> new AssertionError("SOL transaction was not persisted"));

    mockMvc
        .perform(get("/api/v1/me/portfolio").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].assetSymbol").value(org.hamcrest.Matchers.hasItem("SOL")));

    mockMvc
        .perform(
            delete("/api/v1/me/transactions/{transactionId}", savedTransaction.getTransactionId())
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/me/transactions")
                .param("assetSymbol", "SOL")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));

    mockMvc
        .perform(get("/api/v1/me/portfolio").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].assetSymbol")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("SOL"))));

    assertThat(portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), "SOL")).isEmpty();
  }

  @Test
  void deleteTransactionShouldReconcilePortfolioWhenAssetStillHasRemainingHistory()
      throws Exception {
    User user = persistRoleUser("tx-delete-eth-" + UUID.randomUUID() + "@example.com");
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequestForEth("1.0", "2000.00", "first eth buy")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBuyTransactionRequestForEth("0.5", "2100.00", "second eth buy")))
        .andExpect(status().isCreated());

    Transaction secondEthTransaction =
        transactionRepository.findAll().stream()
            .filter(transaction -> transaction.getUser().getId().equals(user.getId()))
            .filter(transaction -> "ETH".equals(transaction.getAssetSymbol()))
            .max(Comparator.comparing(Transaction::getCreatedAt))
            .orElseThrow(() -> new AssertionError("ETH transactions were not persisted"));

    mockMvc
        .perform(
            delete(
                    "/api/v1/me/transactions/{transactionId}",
                    secondEthTransaction.getTransactionId())
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/me/transactions")
                .param("assetSymbol", "ETH")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

    mockMvc
        .perform(get("/api/v1/me/portfolio/ETH").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assetSymbol").value("ETH"))
        .andExpect(jsonPath("$.totalQuantity").value(1.0))
        .andExpect(jsonPath("$.totalInvested").value(2000.00));

    assertThat(portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), "ETH"))
        .hasValueSatisfying(
            entry -> {
              assertThat(entry.getTotalQuantity()).isEqualByComparingTo("1.00000000");
              assertThat(entry.getTotalInvested()).isEqualByComparingTo("2000.00");
            });
  }

  private User persistRoleUser(String email) {
    User user = new User();
    user.setUsername(email.substring(0, email.indexOf('@')));
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode("ValidPass123!"));
    user.setActive(true);
    user.setRoles(new ArrayList<>());
    user.getRoles().add(roleUser());
    return userRepository.save(user);
  }

  private Role roleUser() {
    return roleRepository
        .findByName("ROLE_USER")
        .orElseGet(
            () ->
                roleRepository.save(
                    Role.builder().name("ROLE_USER").description("Standard user role").build()));
  }

  private String loginAndGetAccessToken(String email, String password) throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """
                            .formatted(email, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
  }

  private String idempotencyKey() {
    return UUID.randomUUID().toString();
  }

  private String validTransactionRequest() {
    return """
        {
          "assetSymbol": "BTC",
          "assetType": "CRYPTO",
          "transactionType": "BUY",
          "quantity": 0.50,
          "pricePerUnit": 95000.00,
          "totalValue": 47500.00,
          "transactionDate": "2026-03-07T00:20:00",
          "fee": 10.00,
          "notes": "buy btc"
        }
        """;
  }

  private String validBuyTransactionRequest() {
    return """
        {
          "assetSymbol": "BTC",
          "assetType": "CRYPTO",
          "quantity": 0.50,
          "pricePerUnit": 95000.00,
          "fee": 10.00,
          "transactionDate": "2026-03-07T00:20:00",
          "notes": "buy btc"
        }
        """;
  }

  private String validTransferTransactionRequest() {
    return """
        {
          "assetSymbol": "BTC",
          "assetType": "CRYPTO",
          "transferType": "TRANSFER_IN",
          "quantity": 0.25,
          "fee": 0.0002,
          "transactionDate": "2026-03-10T02:11:00",
          "notes": "transfer btc"
        }
        """;
  }

  private String validBuyTransactionRequestForBnb() {
    return """
        {
          "assetSymbol": "BNB",
          "assetType": "CRYPTO",
          "quantity": 1,
          "pricePerUnit": 757,
          "fee": 0.5,
          "transactionDate": "2025-08-04T04:53:36.3310833",
          "notes": "Prueba"
        }
        """;
  }

  private String validBuyTransactionRequestForAapl() {
    return """
        {
          "assetSymbol": "AAPL",
          "assetType": "STOCK",
          "quantity": 1,
          "pricePerUnit": 248.96,
          "fee": 0.6,
          "transactionDate": "2026-03-19T12:15:00",
          "notes": "buy apple"
        }
        """;
  }

  private String validBuyTransactionRequestForSol() {
    return """
        {
          "assetSymbol": "SOL",
          "assetType": "CRYPTO",
          "quantity": 2,
          "pricePerUnit": 120.55,
          "fee": 0.25,
          "transactionDate": "2026-03-20T09:15:00",
          "notes": "buy sol"
        }
        """;
  }

  private String validBuyTransactionRequestForEth(
      String quantity, String pricePerUnit, String notes) {
    return validBuyTransactionRequestForEthAt(quantity, pricePerUnit, "2026-03-21T11:30:00", notes);
  }

  private String validBuyTransactionRequestForEthAt(
      String quantity, String pricePerUnit, String transactionDate, String notes) {
    return """
        {
          "assetSymbol": "ETH",
          "assetType": "CRYPTO",
          "quantity": %s,
          "pricePerUnit": %s,
          "fee": 0.00,
          "transactionDate": "%s",
          "notes": "%s"
        }
        """
        .formatted(quantity, pricePerUnit, transactionDate, notes);
  }

  private String validUpdateTransactionRequestForEth() {
    return """
        {
          "assetSymbol": "ETH",
          "assetType": "CRYPTO",
          "quantity": 1.25,
          "pricePerUnit": 2800.00,
          "fee": 1.00,
          "transactionDate": "2026-03-22T10:15:00",
          "notes": "edited btc into eth"
        }
        """;
  }

  private String validUpdateTransferTransactionRequest() {
    return """
        {
          "assetSymbol": "ETH",
          "assetType": "CRYPTO",
          "quantity": 0.40,
          "fee": 0.0001,
          "transactionDate": "2026-03-22T10:45:00",
          "notes": "edited transfer",
          "transferType": "TRANSFER_IN"
        }
        """;
  }

  private String invalidUpdateTransferTransactionRequest() {
    return """
        {
          "assetSymbol": "ETH",
          "assetType": "CRYPTO",
          "quantity": 0.40,
          "fee": 0.0001,
          "transactionDate": "2026-03-22T10:45:00",
          "notes": "edited transfer without transferType"
        }
        """;
  }

  private String validUpdateTransactionRequestForMsft() {
    return """
        {
          "assetSymbol": "MSFT",
          "assetType": "STOCK",
          "quantity": 3,
          "pricePerUnit": 411.35,
          "fee": 1.25,
          "transactionDate": "2026-03-22T10:30:00",
          "notes": "edited into microsoft"
        }
        """;
  }
}
