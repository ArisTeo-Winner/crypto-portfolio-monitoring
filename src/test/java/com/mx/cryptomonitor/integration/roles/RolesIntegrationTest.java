package com.mx.cryptomonitor.integration.roles;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@Transactional
class RolesIntegrationTest {

  @Autowired private RoleRepository roleRepository;

  @Test
  void findByNameTest() {

    Optional<Role> defaultRole = roleRepository.findByName("ROLE_USER");

    Role roleBy = defaultRole.get();

    log.info("RESULTADO DE findByNameTest : {}", roleBy.toString());
  }
}
