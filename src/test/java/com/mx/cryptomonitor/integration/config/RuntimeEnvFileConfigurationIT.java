package com.mx.cryptomonitor.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RuntimeEnvFileConfigurationIT {

  private static final Set<String> PLACEHOLDER_VALUES =
      Set.of("__SET_ME__", "__SET_IN_VERCEL_OR_JENKINS__");

  @Test
  void localEnvFileShouldProvideFinnhubApiKey() throws IOException {
    Map<String, String> env = readEnvFile(Path.of(".env"));

    assertRequiredSecret(env, "FINNHUB_API_KEY");
  }

  @Test
  void localFinnhubApiKeyShouldBeAcceptedByFinnhub() throws Exception {
    Map<String, String> env = readEnvFile(Path.of(".env"));
    String apiKey = assertRequiredSecret(env, "FINNHUB_API_KEY");
    String baseUrl = env.getOrDefault("FINNHUB_BASE_URL", "https://finnhub.io/api/v1");

    URI uri =
        URI.create(
            "%s/stock/profile2?symbol=AAPL&token=%s"
                .formatted(
                    baseUrl.replaceAll("/+$", ""),
                    URLEncoder.encode(apiKey, StandardCharsets.UTF_8)));

    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as(
            "Finnhub rejected FINNHUB_API_KEY or the plan/rate limit blocks profile2. Status=%s Body=%s",
            response.statusCode(), summarize(response.body()))
        .isBetween(200, 299);

    String body = response.body() == null ? "" : response.body();
    String normalizedBody = body.toLowerCase(Locale.ROOT);
    assertThat(normalizedBody)
        .as("Finnhub returned a provider error. Body=%s", summarize(body))
        .doesNotContain("invalid api key")
        .doesNotContain("\"error\"")
        .doesNotContain("premium query parameter")
        .doesNotContain("subscription")
        .doesNotContain("rate limit")
        .doesNotContain("limit exceeded");
    assertThat(normalizedBody)
        .as("Finnhub response should contain the AAPL company profile. Body=%s", summarize(body))
        .contains("\"ticker\"", "aapl", "\"name\"");
  }

  @Test
  void envExampleShouldDocumentFinnhubConfiguration() throws IOException {
    Map<String, String> envExample = readEnvFile(Path.of(".env.example"));

    assertThat(envExample).containsKeys("FINNHUB_API_KEY", "FINNHUB_BASE_URL");
  }

  @Test
  void localEnvFileShouldProvideBanxicoToken() throws IOException {
    Map<String, String> env = readEnvFile(Path.of(".env"));

    assertRequiredSecret(env, "BANXICO_TOKEN");
  }

  @Test
  void localBanxicoTokenShouldBeAcceptedByBanxico() throws Exception {
    Map<String, String> env = readEnvFile(Path.of(".env"));
    String token = assertRequiredSecret(env, "BANXICO_TOKEN");
    String baseUrl = env.getOrDefault("BANXICO_BASE_URL", "https://www.banxico.org.mx");

    URI uri =
        URI.create(
            "%s/SieAPIRest/service/v1/series/SF60633/datos/oportuno"
                .formatted(baseUrl.replaceAll("/+$", "")));

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("Bmx-Token", token)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as(
            "Banxico rejected BANXICO_TOKEN or the SIE quota is exhausted. Status=%s Body=%s",
            response.statusCode(), summarize(response.body()))
        .isBetween(200, 299);

    String body = response.body() == null ? "" : response.body();
    assertThat(body)
        .as("Banxico response should contain the SF60633 series data. Body=%s", summarize(body))
        .contains("\"idSerie\"", "SF60633", "\"dato\"");
  }

  @Test
  void envExampleShouldDocumentBanxicoConfiguration() throws IOException {
    Map<String, String> envExample = readEnvFile(Path.of(".env.example"));

    assertThat(envExample).containsKeys("BANXICO_TOKEN", "BANXICO_BASE_URL");
  }

  @Test
  void localEnvFileShouldProvideDataBursatilToken() throws IOException {
    Map<String, String> env = readEnvFile(Path.of(".env"));

    assertRequiredSecret(env, "DATABURSATIL_TOKEN");
  }

  @Test
  void localDataBursatilTokenShouldBeAcceptedByDataBursatil() throws Exception {
    Map<String, String> env = readEnvFile(Path.of(".env"));
    String token = assertRequiredSecret(env, "DATABURSATIL_TOKEN");
    String baseUrl = env.getOrDefault("DATABURSATIL_BASE_URL", "https://api.databursatil.com");

    URI uri =
        URI.create(
            "%s/v2/tasas?token=%s"
                .formatted(
                    baseUrl.replaceAll("/+$", ""),
                    URLEncoder.encode(token, StandardCharsets.UTF_8)));

    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as(
            "DataBursatil rejected DATABURSATIL_TOKEN or the plan/rate limit blocks tasas."
                + " Status=%s Body=%s",
            response.statusCode(), summarize(response.body()))
        .isBetween(200, 299);

    String body = response.body() == null ? "" : response.body();
    assertThat(body)
        .as("DataBursatil response should contain rate data. Body=%s", summarize(body))
        .contains("\"t\":", "\"f\":");
  }

  @Test
  void envExampleShouldDocumentDataBursatilConfiguration() throws IOException {
    Map<String, String> envExample = readEnvFile(Path.of(".env.example"));

    assertThat(envExample).containsKeys("DATABURSATIL_TOKEN", "DATABURSATIL_BASE_URL");
  }

  private Map<String, String> readEnvFile(Path path) throws IOException {
    assertThat(path)
        .as("%s must exist to run backend runtime configuration checks", path)
        .exists()
        .isRegularFile();

    List<String> lines = Files.readAllLines(path);
    Map<String, String> values = new LinkedHashMap<>();
    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
        continue;
      }

      String[] parts = line.split("=", 2);
      values.putIfAbsent(parts[0].trim(), parts[1].trim());
    }
    return values;
  }

  private String assertRequiredSecret(Map<String, String> env, String name) {
    String value = env.get(name);
    assertThat(value)
        .as(
            "%s must be configured in local .env; missing, blank or placeholder values disable"
                + " this integration",
            name)
        .isNotNull()
        .isNotBlank()
        .isNotIn(PLACEHOLDER_VALUES);
    return value;
  }

  private String summarize(String body) {
    if (body == null || body.isBlank()) {
      return "<empty>";
    }
    String sanitized = body.replaceAll("\\s+", " ").trim();
    return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300) + "...";
  }
}
