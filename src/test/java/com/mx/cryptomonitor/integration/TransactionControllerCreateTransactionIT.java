package com.mx.cryptomonitor.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.UpdateTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionDetailsResponse;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.service.TransactionService;
import com.mx.cryptomonitor.transaction.domain.exception.InvalidTransactionException;
import com.mx.cryptomonitor.transaction.domain.exception.TransactionNotFoundException;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.infrastructure.security.oauth.AuthenticatedUserPrincipal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerCreateTransactionIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private TransactionService transactionService;

  @Test
  void createTransactionReturns201WhenAuthenticatedUserPrincipalIsPresent() throws Exception {
    UUID userId = UUID.randomUUID();
    TransactionRequest request = validLegacyRequest();

    when(transactionService.registerTransaction(
            eq(userId), any(TransactionRequest.class), any(String.class)))
        .thenReturn(successResponse("BUY", null));

    mockMvc
        .perform(
            post("/api/v1/me/transactions")
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.assetSymbol").value("BTC"))
        .andExpect(jsonPath("$.transactionType").value("BUY"));

    verify(transactionService)
        .registerTransaction(eq(userId), any(TransactionRequest.class), any(String.class));
  }

  @Test
  void createBuyTransactionReturns201() throws Exception {
    UUID userId = UUID.randomUUID();

    when(transactionService.registerBuyTransaction(
            eq(userId), any(BuyTransactionRequest.class), any(String.class)))
        .thenReturn(successResponse("BUY", null));

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBuyRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("BUY"))
        .andExpect(jsonPath("$.totalValue").value(47500.00));
  }

  @Test
  void createSellTransactionReturns201() throws Exception {
    UUID userId = UUID.randomUUID();

    when(transactionService.registerSellTransaction(
            eq(userId), any(SellTransactionRequest.class), any(String.class)))
        .thenReturn(successResponse("SELL", null));

    mockMvc
        .perform(
            post("/api/v1/me/transactions/sell")
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSellRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("SELL"));
  }

  @Test
  void createTransferTransactionReturns201() throws Exception {
    UUID userId = UUID.randomUUID();

    when(transactionService.registerTransferTransaction(
            eq(userId), any(TransferTransactionRequest.class), any(String.class)))
        .thenReturn(successResponse("TRANSFER", "TRANSFER_IN"));

    mockMvc
        .perform(
            post("/api/v1/me/transactions/transfer")
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("TRANSFER"));
  }

  @Test
  void getTransactionDetailsReturns200ForAuthenticatedUser() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    when(transactionService.getTransactionDetails(userId, transactionId))
        .thenReturn(
            new TransactionDetailsResponse(
                transactionId,
                "BTC",
                AssetType.CRYPTO,
                "BUY",
                null,
                OffsetDateTime.of(2026, 1, 24, 17, 55, 0, 0, ZoneOffset.UTC),
                new BigDecimal("0.25"),
                new BigDecimal("89208.14"),
                new BigDecimal("22302.04"),
                new BigDecimal("0.50"),
                "USD",
                new BigDecimal("22302.54"),
                "Total Spent",
                "Compra manual",
                "MANUAL",
                null,
                "COMPLETED"));

    mockMvc
        .perform(
            get("/api/v1/me/transactions/details/{transactionId}", transactionId)
                .with(authentication(userAuthentication(userId, "ROLE_USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(transactionId.toString()))
        .andExpect(jsonPath("$.amountLabel").value("Total Spent"))
        .andExpect(jsonPath("$.netAmount").value(22302.54));
  }

  @Test
  void deleteTransactionReturns204ForAuthenticatedUser() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/v1/me/transactions/{transactionId}", transactionId)
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey()))
        .andExpect(status().isNoContent());

    verify(transactionService)
        .deleteTransactionById(eq(userId), eq(transactionId), any(String.class));
  }

  @Test
  void deleteTransactionReturns404WhenTransactionDoesNotExist() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    org.mockito.Mockito.doThrow(new TransactionNotFoundException("Transaction no encontrada"))
        .when(transactionService)
        .deleteTransactionById(eq(userId), eq(transactionId), any(String.class));

    mockMvc
        .perform(
            delete("/api/v1/me/transactions/{transactionId}", transactionId)
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Transaction Not Found"));
  }

  @Test
  void updateTransactionReturns200ForAuthenticatedUser() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    when(transactionService.updateTransaction(
            eq(userId), eq(transactionId), any(UpdateTransactionRequest.class), any(String.class)))
        .thenReturn(successResponse("BUY", null));

    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", transactionId)
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validUpdateRequest())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionType").value("BUY"));
  }

  @Test
  void updateTransactionReturns400WhenBusinessValidationFails() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    when(transactionService.updateTransaction(
            eq(userId), eq(transactionId), any(UpdateTransactionRequest.class), any(String.class)))
        .thenThrow(
            new InvalidTransactionException(
                "El precio por unidad debe ser mayor que cero para transacciones BUY o SELL"));

    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", transactionId)
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validUpdateRequest())))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid Transaction"));
  }

  @Test
  void updateTransactionReturns404WhenTransactionDoesNotExist() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    when(transactionService.updateTransaction(
            eq(userId), eq(transactionId), any(UpdateTransactionRequest.class), any(String.class)))
        .thenThrow(new TransactionNotFoundException("Transaction no encontrada"));

    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", transactionId)
                .with(authentication(userAuthentication(userId, "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validUpdateRequest())))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Transaction Not Found"));
  }

  @Test
  void updateTransactionReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", UUID.randomUUID())
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validUpdateRequest())))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

    verifyNoInteractions(transactionService);
  }

  @Test
  void updateTransactionReturns403WhenRoleIsNotUser() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", UUID.randomUUID())
                .with(authentication(userAuthentication(UUID.randomUUID(), "ROLE_ADMIN")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validUpdateRequest())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

    verifyNoInteractions(transactionService);
  }

  @Test
  void updateTransactionReturns400WhenPayloadIsStructurallyInvalid() throws Exception {
    UpdateTransactionRequest invalidRequest =
        new UpdateTransactionRequest("", "", new BigDecimal("-1"), null, null, null, null, null);

    mockMvc
        .perform(
            put("/api/v1/me/transactions/{transactionId}", UUID.randomUUID())
                .with(authentication(userAuthentication(UUID.randomUUID(), "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(transactionService);
  }

  @Test
  void createTransactionReturns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBuyRequest())))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

    verifyNoInteractions(transactionService);
  }

  @Test
  void createTransactionReturns403WhenRoleIsNotUser() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/me/transactions/transfer")
                .with(authentication(userAuthentication(UUID.randomUUID(), "ROLE_ADMIN")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

    verifyNoInteractions(transactionService);
  }

  @Test
  void createTransactionReturns401WhenPrincipalTypeIsUnexpected() throws Exception {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken("user@example.com", null, "ROLE_USER");
    authentication.setAuthenticated(true);

    mockMvc
        .perform(
            post("/api/v1/me/transactions/sell")
                .with(authentication(authentication))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSellRequest())))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

    verifyNoInteractions(transactionService);
  }

  @Test
  void createTransactionReturns400WhenPayloadIsInvalid() throws Exception {
    BuyTransactionRequest invalidRequest =
        new BuyTransactionRequest("", "", new BigDecimal("-1"), null, null, null, null);

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .with(authentication(userAuthentication(UUID.randomUUID(), "ROLE_USER")))
                .header("X-Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(transactionService);
  }

  private TransactionRequest validLegacyRequest() {
    return new TransactionRequest(
        "BTC",
        AssetType.CRYPTO,
        "BUY",
        new BigDecimal("0.50"),
        new BigDecimal("95000.00"),
        new BigDecimal("47500.00"),
        OffsetDateTime.of(2026, 3, 6, 10, 0, 0, 0, ZoneOffset.UTC),
        new BigDecimal("10.00"),
        "buy btc");
  }

  private BuyTransactionRequest validBuyRequest() {
    return new BuyTransactionRequest(
        "BTC",
        "CRYPTO",
        new BigDecimal("0.50"),
        new BigDecimal("95000.00"),
        new BigDecimal("10.00"),
        OffsetDateTime.of(2026, 3, 6, 10, 0, 0, 0, ZoneOffset.UTC),
        "buy btc");
  }

  private SellTransactionRequest validSellRequest() {
    return new SellTransactionRequest(
        "BTC",
        "CRYPTO",
        new BigDecimal("0.50"),
        new BigDecimal("95000.00"),
        new BigDecimal("10.00"),
        OffsetDateTime.of(2026, 3, 6, 10, 0, 0, 0, ZoneOffset.UTC),
        "sell btc");
  }

  private TransferTransactionRequest validTransferRequest() {
    return new TransferTransactionRequest(
        "BTC",
        "CRYPTO",
        "TRANSFER_IN",
        new BigDecimal("0.25"),
        new BigDecimal("0.0002"),
        OffsetDateTime.of(2026, 3, 10, 2, 11, 0, 0, ZoneOffset.UTC),
        "transfer btc");
  }

  private UpdateTransactionRequest validUpdateRequest() {
    return new UpdateTransactionRequest(
        "BTC",
        "CRYPTO",
        new BigDecimal("0.75"),
        new BigDecimal("91000.00"),
        OffsetDateTime.of(2026, 3, 8, 9, 0, 0, 0, ZoneOffset.UTC),
        new BigDecimal("15.00"),
        "edited btc",
        null);
  }

  private TransactionResponse successResponse(String transactionType, String transferType) {
    return new TransactionResponse(
        UUID.randomUUID(),
        "BTC",
        "CRYPTO",
        transactionType,
        new BigDecimal("0.50"),
        new BigDecimal("95000.00"),
        new BigDecimal("47500.00"),
        OffsetDateTime.of(2026, 3, 6, 10, 0, 0, 0, ZoneOffset.UTC),
        new BigDecimal("10.00"),
        transferType == null ? "buy btc" : "transfer btc",
        OffsetDateTime.of(2026, 3, 6, 10, 0, 0, 0, ZoneOffset.UTC),
        OffsetDateTime.of(2026, 3, 6, 10, 0, 0, 0, ZoneOffset.UTC));
  }

  private TestingAuthenticationToken userAuthentication(UUID userId, String role) {
    User user = User.builder().id(userId).email("user@example.com").passwordHash("hash").build();
    AuthenticatedUserPrincipal principal =
        new AuthenticatedUserPrincipal(
            user, Map.of("sub", user.getEmail()), List.of(new SimpleGrantedAuthority(role)));
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(principal, null, role);
    authentication.setAuthenticated(true);
    return authentication;
  }

  private String idempotencyKey() {
    return UUID.randomUUID().toString();
  }
}
