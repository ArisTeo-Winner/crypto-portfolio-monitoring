package com.mx.cryptomonitor.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OidcLoginHappyPathIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void oidcAuthenticatedUserCanReachDashboard() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/auth/dashboard")
                .with(
                    oidcLogin()
                        .userInfoToken(userInfo -> userInfo.claim("name", "Alan"))
                        .idToken(
                            token ->
                                token.claim("sub", "sub-1").claim("email", "alan@example.com"))))
        .andExpect(status().isOk())
        .andExpect(content().string("Bienvenido, Alan"));
  }
}
