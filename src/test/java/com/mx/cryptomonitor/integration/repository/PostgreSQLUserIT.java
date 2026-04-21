package com.mx.cryptomonitor.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@DataJpaTest
@ActiveProfiles("test")
public class PostgreSQLUserIT extends InfraIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Test
  void shouldSaveUserFromPostgreSQL() {
    User user = new User();
    user.setUsername("PostgreSQL");
    user.setEmail("test-pg@example.com");
    user.setFirstName("Usuario PostgreSQL");
    user.setLastName("SQL");

    User saved = userRepository.save(user);
    userRepository.flush();

    User found = userRepository.findByEmail("test-pg@example.com").orElse(null);
    assertThat(found).isNotNull();
    assertThat(found.getFirstName()).isEqualTo("Usuario PostgreSQL");
    assertThat(saved.getId()).isNotNull();
  }
}
