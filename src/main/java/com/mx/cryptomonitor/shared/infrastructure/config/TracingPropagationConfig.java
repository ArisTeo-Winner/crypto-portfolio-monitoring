package com.mx.cryptomonitor.shared.infrastructure.config;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

@Configuration
public class TracingPropagationConfig {

  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String REQUEST_ID_KEY = "requestId";

  @Bean
  WebClientCustomizer tracingWebClientCustomizer(Tracer tracer, Propagator propagator) {
    return builder ->
        builder.filter(
            (request, next) -> {
              ClientRequest.Builder tracedRequest = ClientRequest.from(request);
              injectTraceHeaders(tracer, propagator, tracedRequest);
              propagateRequestIdIfPresent(
                  tracedRequest, request.headers().containsKey(REQUEST_ID_HEADER));
              return next.exchange(tracedRequest.build());
            });
  }

  @Bean
  RestTemplateCustomizer tracingRestTemplateCustomizer(Tracer tracer, Propagator propagator) {
    return restTemplate ->
        restTemplate
            .getInterceptors()
            .add(
                (request, body, execution) -> {
                  injectTraceHeaders(tracer, propagator, request.getHeaders());
                  propagateRequestIdIfPresent(
                      request.getHeaders(), request.getHeaders().containsKey(REQUEST_ID_HEADER));
                  return execution.execute(request, body);
                });
  }

  private void injectTraceHeaders(
      Tracer tracer, Propagator propagator, ClientRequest.Builder request) {
    Span currentSpan = tracer.currentSpan();
    if (currentSpan == null) {
      return;
    }
    propagator.inject(currentSpan.context(), request, ClientRequest.Builder::header);
  }

  private void injectTraceHeaders(
      Tracer tracer, Propagator propagator, org.springframework.http.HttpHeaders headers) {
    Span currentSpan = tracer.currentSpan();
    if (currentSpan == null) {
      return;
    }
    propagator.inject(currentSpan.context(), headers, org.springframework.http.HttpHeaders::set);
  }

  private void propagateRequestIdIfPresent(ClientRequest.Builder request, boolean alreadyPresent) {
    if (alreadyPresent) {
      return;
    }
    String requestId = MDC.get(REQUEST_ID_KEY);
    if (StringUtils.hasText(requestId)) {
      request.header(REQUEST_ID_HEADER, requestId);
    }
  }

  private void propagateRequestIdIfPresent(
      org.springframework.http.HttpHeaders headers, boolean alreadyPresent) {
    if (alreadyPresent) {
      return;
    }
    String requestId = MDC.get(REQUEST_ID_KEY);
    if (StringUtils.hasText(requestId)) {
      headers.set(REQUEST_ID_HEADER, requestId);
    }
  }
}
