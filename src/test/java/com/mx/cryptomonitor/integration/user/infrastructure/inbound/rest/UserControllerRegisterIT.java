package com.mx.cryptomonitor.integration.user.infrastructure.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

/**
 * Integration smoke test (full Spring context) for {@code POST /api/v1/users/register}.
 *
 * <p>Test pyramid: integration. Package mirrors {@code src/main} controller location under {@code
 * user/infrastructure/inbound/rest}.
 */
@SpringBootTest(classes = CryptoPortfolioMonitoringApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "security.registration-rate-limit.enabled=false")
class UserControllerRegisterIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  @Test
  void registerUser_happyPath_thenDuplicateReturns409() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String username = "it_user_" + suffix;
    String email = "it_" + suffix + "@example.com";

    UserRegistrationRequest request =
        new UserRegistrationRequest(
            username,
            email,
            "StrongP@ssw0rd!2026",
            "Leo",
            "Manzano",
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    String payload = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.email").value(email))
        // No secrets in responses
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    mockMvc
        .perform(
            post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("REGISTRATION_CONFLICT"))
        .andExpect(
            jsonPath("$.detail").value("Registration cannot be completed with the provided data."));
  }

  @Test
  void registerUser_shouldPersistNonNullUpdatedAt() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String username = "it_user_ts_" + suffix;
    String email = "it_ts_" + suffix + "@example.com";

    UserRegistrationRequest request =
        new UserRegistrationRequest(
            username,
            email,
            "StrongP@ssw0rd!2026",
            "Nora",
            "Campos",
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    User persistedUser =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new AssertionError("User not persisted"));

    assertThat(persistedUser.getCreatedAt()).isNotNull();
    assertThat(persistedUser.getUpdatedAt()).isNotNull();
    assertThat(persistedUser.getUpdatedAt()).isEqualTo(persistedUser.getCreatedAt());
  }
}
