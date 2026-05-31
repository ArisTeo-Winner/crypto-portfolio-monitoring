package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.application.service.UserService;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class UserControllerIntegrationTest {

  private final Logger logger = LoggerFactory.getLogger(UserControllerIntegrationTest.class);

  @Autowired private UserRepository userRepository;

  @Autowired private RoleRepository roleRepository;

  @Autowired private UserService userService;

  @BeforeEach
  public void setUp() {

    UserRegistrationRequest registrationRequest =
        new UserRegistrationRequest(
            "Alan_doe",
            "alan@example.com",
            "securepassword",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    userService.registerUser(registrationRequest);
  }

  @Test
  void testUserByEmail() {

    Role userRole =
        roleRepository
            .findByName("ROLE_USER")
            .orElseThrow(() -> new RuntimeException("El rol no existe"));

    logger.info("Respuesta de roleRepository.findByName::: {}", userRole);

    String email = "alan@example.com";

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

    logger.info("Respuesta de userRepository.findById::: {}", user);
  } /**/

  /*
      @Test
      public void testLoginGeneratesTokensAndCapturesIp() throws Exception {

      	logger.info("---UserControllerIntegrationTest >>> testLoginGeneratesTokensAndCapturesIp()---");

      	UUID userId = UUID.fromString("e7c11796-b0dd-4170-b0c1-ff4807d50737");


          // Arrange
          String email = "johndoe@example.com";
          String password = "password123";
          String ipAddress = "127.0.0.1";
          String userAgent = "Mozilla/5.0";

          refreshToken = new RefreshToken();

          User user = userRepository.findByEmail(email)
                  .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

          user.setEmail(email);
          user.setPasswordHash(password); // Ajusta según tu implementación
          //userRepository.save(user);

          LoginRequest loginRequest = new LoginRequest(email, password);

          // Act
          mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(loginRequest)))
                  .andExpect(MockMvcResultMatchers.status().isOk())
                  .andExpect(MockMvcResultMatchers.jsonPath("$.accessToken").exists())
                  .andExpect(MockMvcResultMatchers.jsonPath("$.refreshToken").exists());

          // Assert
          List<RefreshToken> lista = refreshTokenRepository.findByUser(user);

          assertNotNull(lista);

          //assertThat(lista).hasSize(6);


          lista.forEach(token -> {
              System.out.printf("id: %s, user: %s, Token: %s, Created: %s, Expires: %s, LastUsed: %s, IP: %s, Agent: %s, RevokedAt: %s%n",
                      token.getId(),
                      token.getUser(),
                      token.getRefreshToken(),
                      token.getCreatedAt(),
                      token.getExpiresAt(),
                      token.getLastUsedAt(),
                      token.getIpAddress(),
                      token.getUserAgent(),
                      token.getRevokedAt());
          });


          // Paso 3: Verificar resultados
          assertNotNull(lista, "La lista no debería ser nula");
          assertEquals(7, lista.size(), "Debería haber 1 token por usuario");

          lista.stream()
          	 .forEach(p -> System.out.printf("%-20s %-10d %-15s%n",
          			 p.getId(),p.getRefreshToken(),p.getUser().getEmail()));
          assertThat(lista).hasSize(3);

  //        assertEquals(ipAddress, refreshToken.getIpAddress());
      }*/
}
