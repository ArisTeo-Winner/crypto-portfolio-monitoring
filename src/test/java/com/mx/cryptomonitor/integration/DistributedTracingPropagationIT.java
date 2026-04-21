package com.mx.cryptomonitor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@SpringBootTest
@AutoConfigureObservability
@ActiveProfiles("test")
class DistributedTracingPropagationIT {

  @Autowired private WebClient webClient;
  @Autowired private RestTemplate restTemplate;
  @Autowired private Tracer tracer;

  private MockWebServer mockWebServer;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (mockWebServer != null) {
      mockWebServer.shutdown();
    }
    org.slf4j.MDC.remove("requestId");
  }

  @Test
  void webClientShouldPropagateTraceHeadersAndRequestId() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

    Span span = tracer.nextSpan().name("webclient-propagation").start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      org.slf4j.MDC.put("requestId", "req-webclient-1");
      webClient
          .get()
          .uri(mockWebServer.url("/webclient").uri())
          .retrieve()
          .bodyToMono(String.class)
          .block(Duration.ofSeconds(5));
    } finally {
      span.end();
    }

    RecordedRequest request = mockWebServer.takeRequest();
    assertTracingHeaders(request);
    assertThat(request.getHeader("X-Request-Id")).isEqualTo("req-webclient-1");
  }

  @Test
  void restTemplateShouldPropagateTraceHeadersAndRequestId() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

    Span span = tracer.nextSpan().name("resttemplate-propagation").start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      org.slf4j.MDC.put("requestId", "req-resttemplate-1");
      String response =
          restTemplate.getForObject(mockWebServer.url("/resttemplate").uri(), String.class);
      assertThat(response).isEqualTo("ok");
    } finally {
      span.end();
    }

    RecordedRequest request = mockWebServer.takeRequest();
    assertTracingHeaders(request);
    assertThat(request.getHeader("X-Request-Id")).isEqualTo("req-resttemplate-1");
  }

  private void assertTracingHeaders(RecordedRequest request) {
    assertThat(
            request.getHeader("traceparent") != null
                || request.getHeader("b3") != null
                || request.getHeader("X-B3-TraceId") != null)
        .isTrue();
  }
}
