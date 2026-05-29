package com.mx.cryptomonitor.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.service.TransactionService;
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
class TransactionMeJwtConsistencyIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private TransactionService transactionService;
  @MockBean private RefreshTokenStoreService refreshTokenStoreService;

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
  }

  @Test
  void sameAccessTokenShouldAllowGetAndPost_whenUserHasRoleUser() throws Exception {
    User user = persistUserWithRoles("jwt-ok-" + UUID.randomUUID() + "@example.com", true);
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    when(transactionService.getTransactionsUser(eq(user.getId()), any(), any(), any()))
        .thenReturn(List.of());
    when(transactionService.registerTransaction(eq(user.getId()), any(), anyString()))
        .thenReturn(successfulTransactionResponse());

    mockMvc
        .perform(
            get("/api/v1/me/transactions")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    mockMvc
        .perform(
            post("/api/v1/me/transactions")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransactionRequest()))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.assetSymbol").value("BTC"))
        .andExpect(jsonPath("$.transactionType").value("BUY"));
  }

  @Test
  void sameAccessTokenShouldRejectGetAndPostConsistently_whenUserHasNoRoleUser() throws Exception {
    User user = persistUserWithRoles("jwt-no-role-" + UUID.randomUUID() + "@example.com", false);
    String accessToken = loginAndGetAccessToken(user.getEmail(), "ValidPass123!");

    mockMvc
        .perform(
            get("/api/v1/me/transactions")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    mockMvc
        .perform(
            post("/api/v1/me/transactions")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransactionRequest()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  private User persistUserWithRoles(String email, boolean includeRoleUser) {
    User user = new User();
    user.setUsername(email.substring(0, email.indexOf('@')));
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode("ValidPass123!"));
    user.setActive(true);
    user.setRoles(new ArrayList<>());
    if (includeRoleUser) {
      user.getRoles().add(roleUser());
    }
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

  private TransactionResponse successfulTransactionResponse() {
    return new TransactionResponse(
        UUID.randomUUID(),
        "BTC",
        "CRYPTO",
        "BUY",
        new BigDecimal("0.50"),
        new BigDecimal("95000.00"),
        new BigDecimal("47500.00"),
        OffsetDateTime.of(2026, 3, 6, 12, 0, 0, 0, ZoneOffset.UTC),
        new BigDecimal("10.00"),
        "buy btc",
        OffsetDateTime.of(2026, 3, 6, 12, 0, 0, 0, ZoneOffset.UTC),
        OffsetDateTime.of(2026, 3, 6, 12, 0, 0, 0, ZoneOffset.UTC));
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
          "transactionDate": "2026-03-06T12:00:00",
          "fee": 10.00,
          "notes": "buy btc"
        }
        """;
  }
}
