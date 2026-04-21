package com.mx.cryptomonitor.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.mx.cryptomonitor.integration.support.ContainersConfig;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@Import(ContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT {

  @Autowired private UserRepository userRepository;

  @Test
  void shouldSaveAndFindUserByEmail() {

    User u = new User();
    u.setEmail("satoshi@btc.org");
    u.setUsername("Satoshi");
    u.setFirstName("Satoshi");
    u.setPasswordHash("r3r3r.r3r");

    var saved = userRepository.saveAndFlush(u);

    assertThat(saved.getId()).isNotNull();

    Optional<User> found = userRepository.findByEmail("satoshi@btc.org");
    // assertThat(found).isPresent();
    // assertThat(found.get().getFirstName()).isEqualTo("Satoshi");

    assertThat(found).isPresent().get().extracting(User::getUsername).isEqualTo("Satoshi");
  }
}
