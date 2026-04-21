package com.mx.cryptomonitor.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryIntegrationTest {

  private final Logger logger = LoggerFactory.getLogger(UserRepositoryIntegrationTest.class);

  @Autowired private UserRepository userRepository;

  /*
  @BeforeEach
  public void setUp() {
  	userRepository.deleteAll();
  }
  */

  @Test
  void registerUser_success() {

    logger.info(
        "=== Ejecutando método registerUser_success() desde UserRepositoryIntegrationTest ===");

    String uniqueEmail = "test+" + UUID.randomUUID() + "@example.com";

    User user = new User();
    user.setEmail(uniqueEmail);
    user.setUsername("testUser");
    user.setFirstName("firsUser");
    user.setPasswordHash("NULL");

    userRepository.save(user);

    Optional<User> result = userRepository.findByEmail(uniqueEmail);
    assertTrue(result.isPresent());
    // assertEquals("testUser", result.get().getUsername());
    assertThat(result.get().getEmail()).isEqualTo(uniqueEmail);
  }

  @Test
  void listUser_success() {
    logger.info("=== Ejecutando método listUser_success() desde UserRepositoryIntegrationTest ===");

    List<User> result = userRepository.findAll();

    assertThat(result).isNotNull();
    assertNotNull(result);
  }
}
