package com.mx.cryptomonitor.shared.infrastructure.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

  private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";
  private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/UserApiProblemDetail";
  private static final String BEARER_AUTH_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT access token obtained from /api/v1/auth/login"))
                .addSchemas("UserApiProblemDetail", apiProblemDetailSchema())
                .addResponses("ProblemBadRequest", problemResponse("Invalid request"))
                .addResponses("ProblemUnauthorized", problemResponse("Authentication required"))
                .addResponses("ProblemForbidden", problemResponse("Access denied"))
                .addResponses("ProblemNotFound", problemResponse("Resource not found"))
                .addResponses("ProblemConflict", problemResponse("Business conflict"))
                .addResponses("ProblemRateLimited", problemResponse("Rate limit exceeded"))
                .addResponses("ProblemInternalError", problemResponse("Internal server error")))
        .info(
            new Info()
                .title("Crypto portfolio monitorig API")
                .version("1.0")
                .description(
                    "Documentation for the crypto portfolio monitoring API with RFC 9457 problem responses.")
                .license(new License().name("Apache 2.0").url("https://springdoc.org")));
  }

  @Bean
  public OpenApiCustomizer standardProblemResponsesCustomizer() {
    return openApi -> {
      if (openApi.getPaths() == null) {
        return;
      }

      openApi
          .getPaths()
          .entrySet()
          .forEach(
              pathEntry -> {
                pathEntry
                    .getValue()
                    .readOperations()
                    .forEach(
                        operation -> {
                          ApiResponses responses = operation.getResponses();
                          if (responses == null) {
                            responses = new ApiResponses();
                            operation.setResponses(responses);
                          }
                          addProblemResponse(responses, "400", "Invalid request");
                          addProblemResponse(responses, "401", "Authentication required");
                          addProblemResponse(responses, "403", "Access denied");
                          addProblemResponse(responses, "404", "Resource not found");
                          addProblemResponse(responses, "409", "Business conflict");
                          addProblemResponse(responses, "429", "Rate limit exceeded");
                          addProblemResponse(responses, "500", "Internal server error");

                          if (requiresBearerAuth(pathEntry.getKey())) {
                            operation.addSecurityItem(
                                new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
                          }
                        });
              });
    };
  }

  private boolean requiresBearerAuth(String path) {
    return path.startsWith("/api/v1/me/")
        || path.startsWith("/api/v1/users/me")
        || path.startsWith("/api/v1/tokens/revoke");
  }

  private void addProblemResponse(ApiResponses responses, String code, String description) {
    ApiResponse response = responses.get(code);
    if (response == null) {
      responses.addApiResponse(code, problemResponse(description));
      return;
    }

    if (response.getDescription() == null || response.getDescription().isBlank()) {
      response.setDescription(description);
    }

    if (response.getContent() == null) {
      response.setContent(new Content());
    }
    if (!response.getContent().containsKey(PROBLEM_MEDIA_TYPE)) {
      response
          .getContent()
          .addMediaType(
              PROBLEM_MEDIA_TYPE, new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF)));
    }
  }

  private ApiResponse problemResponse(String description) {
    return new ApiResponse()
        .description(description)
        .content(
            new Content()
                .addMediaType(
                    PROBLEM_MEDIA_TYPE,
                    new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF))));
  }

  private ObjectSchema apiProblemDetailSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.addRequiredItem("type");
    schema.addRequiredItem("title");
    schema.addRequiredItem("status");
    schema.addRequiredItem("detail");
    schema.addRequiredItem("instance");
    schema.addRequiredItem("timestamp");
    schema.addRequiredItem("errorCode");
    schema.addRequiredItem("traceId");

    schema.addProperty(
        "type",
        new StringSchema()
            .description("Problem type URI")
            .example("https://api.cryptomonitor.com/problems/validation-error"));
    schema.addProperty(
        "title", new StringSchema().description("Short summary").example("Validation Error"));
    schema.addProperty("status", new IntegerSchema().description("HTTP status code").example(400));
    schema.addProperty(
        "detail",
        new StringSchema()
            .description("Human readable explanation")
            .example("One or more fields are invalid."));
    schema.addProperty(
        "instance",
        new StringSchema()
            .description("URI reference for this occurrence")
            .example("/api/v1/users/register"));
    schema.addProperty("timestamp", new DateTimeSchema().description("UTC timestamp"));
    schema.addProperty("errorCode", new StringSchema().description("Stable internal error code"));
    schema.addProperty(
        "traceId", new StringSchema().description("Correlation id for troubleshooting"));
    schema.addProperty(
        "errors",
        new ArraySchema()
            .items(new StringSchema())
            .description("Optional list of validation or business errors"));

    return schema;
  }
}
