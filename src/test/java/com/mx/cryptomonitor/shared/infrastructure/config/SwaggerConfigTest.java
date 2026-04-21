package com.mx.cryptomonitor.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SwaggerConfigTest {

  private final SwaggerConfig swaggerConfig = new SwaggerConfig();

  @Test
  void customOpenApiIncludesJwtBearerSchemeAndProblemResponses() {
    var openApi = swaggerConfig.customOpenAPI();

    assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    assertThat(openApi.getComponents().getResponses())
        .containsKeys(
            "ProblemBadRequest",
            "ProblemUnauthorized",
            "ProblemForbidden",
            "ProblemNotFound",
            "ProblemConflict",
            "ProblemRateLimited",
            "ProblemInternalError");
  }
}
