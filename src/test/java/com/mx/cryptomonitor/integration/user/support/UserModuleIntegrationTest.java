package com.mx.cryptomonitor.integration.user.support;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.user.application.dto.request.LoginRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.redis.testcontainers.RedisContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for full-stack integration tests in the user module.
 *
 * <p>Starts real PostgreSQL and Redis containers via Testcontainers so that
 * session, token, and authentication tests exercise the actual storage layer.
 *
 * <p>Subclasses inherit:
 * <ul>
 *   <li>{@link #mockMvc} — pre-configured MockMvc over the full Spring context</li>
 *   <li>{@link #objectMapper} — shared Jackson mapper</li>
 *   <li>{@link #registerAndLogin} — helper to create a fresh user and obtain tokens</li>
 * </ul>
 */
@SpringBootTest(
    classes = CryptoPortfolioMonitoringApplication.class,
    properties = "spring.main.lazy-initialization=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class UserModuleIntegrationTest {

  // -------------------------------------------------------------------------
  // Containers — shared across all tests in the same JVM fork
  // @ServiceConnection lets Spring Boot auto-wire host/port without manual
  // @DynamicPropertySource entries for datasource URL or Redis host.
  // -------------------------------------------------------------------------

  @Container
  @ServiceConnection
  protected static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Container
  @ServiceConnection
  protected static final RedisContainer redis =
      new RedisContainer(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  // -------------------------------------------------------------------------
  // Additional property overrides that @ServiceConnection does not cover
  // -------------------------------------------------------------------------

  @DynamicPropertySource
  static void configureInfrastructure(DynamicPropertyRegistry registry) {
    // JPA — use the Testcontainers Postgres, not H2 from application-test.properties
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add(
        "spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    registry.add(
        "spring.jpa.properties.hibernate.dialect",
        () -> "org.hibernate.dialect.PostgreSQLDialect");
    registry.add("spring.flyway.enabled", () -> "false");

    // JWT — deterministic secret so tokens are reproducible within a test run
    registry.add("jwt.secret-base64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    registry.add("jwt.access-token-expiration", () -> "3600000");
    registry.add("jwt.refresh-token-expiration", () -> "604800000");

    // Rate limiting — disable to avoid interference between tests
    registry.add("security.login-rate-limit.enabled", () -> "false");
    registry.add("security.registration-rate-limit.enabled", () -> "false");
  }

  // -------------------------------------------------------------------------
  // Spring beans available to subclasses
  // -------------------------------------------------------------------------

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  @Autowired private RoleRepository roleRepository;

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  @BeforeEach
  void seedRequiredRoles() {
    roleRepository
        .findByName("ROLE_USER")
        .orElseGet(
            () ->
                roleRepository.save(
                    Role.builder().name("ROLE_USER").description("Default user role").build()));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Registers a fresh user with a random suffix and immediately logs in.
   *
   * @return the access and refresh tokens issued by the server
   */
  protected Tokens registerAndLogin() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String email = "user_" + suffix + "@test.local";
    String password = "StrongP@ssw0rd!2026";

    UserRegistrationRequest registration =
        new UserRegistrationRequest(
            "user_" + suffix, email, password, "Test", "User",
            null, null, null, null, null, null, null);

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isCreated());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andReturn();

    String body = loginResult.getResponse().getContentAsString();
    return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
  }

  /** Access and refresh tokens returned by the login endpoint. */
  protected record Tokens(String accessToken, String refreshToken) {}
}
