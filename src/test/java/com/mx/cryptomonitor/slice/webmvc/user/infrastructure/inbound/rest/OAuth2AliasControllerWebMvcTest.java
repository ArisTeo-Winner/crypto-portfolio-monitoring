package com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.user.infrastructure.inbound.rest.OAuth2AliasController;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;

@WebMvcTest(controllers = OAuth2AliasController.class)
@AutoConfigureMockMvc(addFilters = false)
class OAuth2AliasControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private JwtRequestFilter jwtRequestFilter;

  @Test
  void authorizeGoogleRedirectsToSpringSecurityAuthorizationEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/v1/oauth2/authorize/google"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", "/oauth2/authorization/google"));
  }
}
