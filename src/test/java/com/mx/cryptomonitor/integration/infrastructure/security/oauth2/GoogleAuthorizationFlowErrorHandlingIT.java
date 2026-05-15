package com.mx.cryptomonitor.integration.infrastructure.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleAuthorizationFlowErrorHandlingIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void authorizationEndpointShouldCreateSessionForGoogleFlow() throws Exception {
    MvcResult start =
        mockMvc
            .perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    assertThat(start.getRequest().getSession(false)).isNotNull();
    assertThat(start.getResponse().getHeader("Location"))
        .startsWith("https://accounts.google.com/o/oauth2/v2/auth");
  }

  @Test
  void callbackWithoutAuthorizationSessionShouldRedirectWithUsefulErrorCode() throws Exception {
    MvcResult start =
        mockMvc
            .perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    String location = start.getResponse().getHeader("Location");
    assertThat(location).isNotBlank();
    String state =
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
    assertThat(state).isNotBlank();
    state = URLDecoder.decode(state, StandardCharsets.UTF_8);

    mockMvc
        .perform(get("/login/oauth2/code/google").param("code", "test-code").param("state", state))
        .andExpect(status().is3xxRedirection())
        .andDo(
            result ->
                assertThat(result.getResponse().getHeader("Location"))
                    .isEqualTo(
                        "http://localhost:3000/login?oauth_error=OIDC_LOGIN_FAILED&oauth_error_code=authorization_request_not_found"));
  }
}
