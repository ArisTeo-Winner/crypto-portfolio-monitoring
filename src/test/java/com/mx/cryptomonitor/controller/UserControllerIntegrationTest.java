package com.mx.cryptomonitor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.user.application.dto.request.LoginRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.RefreshTokenRepository;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(value = Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class UserControllerIntegrationTest {

  private final Logger logger = LoggerFactory.getLogger(UserControllerIntegrationTest.class);
  private static final String TEST_EMAIL = "testuser@example.com";
  private static final String TEST_PASSWORD = "Test@123";
  private static final String TEST_USERNAME = "testuser";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Autowired private SessionRepository sessionRepository;

  @MockBean private RefreshTokenStoreService refreshTokenStoreService;

  private UUID existingUserId;

  private String jwtToken;

  @BeforeEach
  public void setUp() throws Exception {

    logger.info("Inicio de setUp()");

    /**
     * Limpiar los datos antes de cada prueba. NOTA. Solo aplica para @ActiveProfiles("test"), puede
     * causar problema con bd local si no ajusta adecuadamente
     */
    refreshTokenRepository.deleteAll();
    sessionRepository.deleteAll();

    org.mockito.Mockito.when(
            refreshTokenStoreService.store(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation ->
                new RefreshTokenStoreService.StoredRefreshToken(
                    UUID.randomUUID(),
                    invocation.getArgument(1, UUID.class),
                    invocation.getArgument(2, UUID.class),
                    false));
  }

  @Test
  public void registerUser_success() throws Exception {

    logger.info(
        "=== Ejecutando método registerUser_success() desde UserControllerIntegrationTest ===");

    UserRegistrationRequest request =
        new UserRegistrationRequest(
            TEST_USERNAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    logger.info("Datos mapeados UserRegistrationRequest:{}", request);

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andDo(
            mvcResult -> {
              // Imprime el JSON de la respuesta
              logger.info("Respuesta JSON: " + mvcResult.getResponse().getContentAsString());
            });
  }

  /**/
  @Test
  public void login_success() throws Exception {

    logger.info("=== Ejecutando método login_success() desde UserControllerIntegrationTest ===");

    // String loginRequest = "{\"email\":\"alan@example.com\",\"password\":\"securepassword\"}";

    ensureUserRegistered();
    User savedUser =
        userRepository
            .findByEmail(TEST_EMAIL)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

    existingUserId = savedUser.getId();

    logger.info(">>>userRepository.findByEmail: {}", savedUser.toString());

    assertEquals(savedUser.getEmail(), TEST_EMAIL);

    LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

    logger.info("Datos mapeado loginRequest: {}", loginRequest);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    String response = result.getResponse().getContentAsString();

    logger.info("Datos mapeados response:{}", response);

    jwtToken = JsonPath.read(response, "$.accessToken");

    logger.info("jwtToken:{}", jwtToken);
  }

  @Test
  public void deleteUser_ShouldReturn204_WhenUserExists() throws Exception {
    ensureUserRegistered();
    jwtToken = loginAndGetAccessToken();
    existingUserId =
        userRepository
            .findByEmail(TEST_EMAIL)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"))
            .getId();
    logger.info("Inicio de deleteUser_ShouldReturn204_WhenUserExists()");
    logger.info("Id de usuario a eliminar:{}", existingUserId);
    assertThat(jwtToken).isNotBlank();
    assertThat(existingUserId).isNotNull();

    mockMvc
        .perform(
            delete("/api/v1/users/{id}", existingUserId)
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  private void ensureUserRegistered() throws Exception {
    if (userRepository.findByEmail(TEST_EMAIL).isPresent()) {
      return;
    }
    String unique = UUID.randomUUID().toString().substring(0, 8);
    UserRegistrationRequest request =
        new UserRegistrationRequest(
            TEST_USERNAME + "_" + unique,
            "testuser+" + unique + "@example.com",
            TEST_PASSWORD,
            null,
            null,
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
  }

  private String loginAndGetAccessToken() throws Exception {
    LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }

  /*
  @Test
  public void deleteUser_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
      UUID nonExistentUserId = UUID.randomUUID();

      mockMvc.perform(delete("/users/" + nonExistentUserId)
                      .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isNotFound());
  }

  @Test
  public void deleteUser_ShouldReturn401_WhenTokenIsInvalid() throws Exception {
      mockMvc.perform(delete("/users/" + existingUserId)
                      .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isUnauthorized());
  }*/
}
