package com.mx.cryptomonitor.contract.consumer.auth;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-side Pact test for POST /api/v1/auth/login.
 *
 * <p>Generates pacts/frontend-crypto-portfolio-api.json when executed. That file is the contract
 * that the provider verification test (Phase 3) must satisfy.
 */
@Tag("contract")
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "crypto-portfolio-api", pactVersion = PactSpecVersion.V3)
class AuthLoginConsumerPactTest {

  @Pact(consumer = "frontend", provider = "crypto-portfolio-api")
  RequestResponsePact loginConCredencialesValidas(PactDslWithProvider builder) {
    return builder
        .given("un usuario registrado con email valid@test.com")
        .uponReceiving("POST /api/v1/auth/login con credenciales validas")
        .method("POST")
        .path("/api/v1/auth/login")
        .headers(Map.of("Content-Type", "application/json"))
        .body(
            new PactDslJsonBody()
                .stringType("email", "valid@test.com")
                .stringType("password", "Secret123!"))
        .willRespondWith()
        .status(200)
        .body(new PactDslJsonBody().stringType("accessToken").stringType("refreshToken"))
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "loginConCredencialesValidas")
  void debeRetornar200ConTokensAlLoginExitoso(MockServer mockServer) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String payload = "{\"email\":\"valid@test.com\",\"password\":\"Secret123!\"}";
    HttpEntity<String> request = new HttpEntity<>(payload, headers);

    ResponseEntity<String> response =
        new RestTemplate()
            .postForEntity(mockServer.getUrl() + "/api/v1/auth/login", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("accessToken", "refreshToken");
  }
}
