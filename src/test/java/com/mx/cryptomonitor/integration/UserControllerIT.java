package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class UserControllerIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private RoleRepository roleRepository;

  @Autowired private ObjectMapper objectMapper; // 🔥 Para convertir objetos en JSON

  @BeforeEach
  void setUp() {
    // 🔥 Inserta rol en BD antes de la prueba
    if (roleRepository.findByName("ROLE_USER").isEmpty()) {
      Role userRole = new Role();
      userRole.setName("ROLE_USER");
      userRole.setDescription("Rol de usuario normal");
      roleRepository.save(userRole);
    }
  }

  @Test
  @WithMockUser(roles = "USER")
  void testRegisterUser_Success() throws Exception {

    // 🔥 Simula solicitud de registro
    UserRegistrationRequest request =
        new UserRegistrationRequest(
            "testuser",
            "test@example.com",
            "Password123!",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    // Actúa como un cliente real llamando al endpoint
    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated()) // 🔥 Verifica que retorna 201
        .andExpect(jsonPath("$.username").value("testuser"))
        .andExpect(jsonPath("$.email").value("test@example.com"));

    // 🔍 Verifica que el usuario realmente se guardó en la BD
    Optional<User> savedUser = userRepository.findByEmail("test@example.com");
    assertTrue(savedUser.isPresent());
    assertEquals("testuser", savedUser.get().getUsername());
    assertTrue(savedUser.get().isActive());
  }
}
