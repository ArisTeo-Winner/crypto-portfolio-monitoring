package com.mx.cryptomonitor.user.infrastructure.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {
    ProblemDetail problem = buildUnauthorizedProblem(request);

    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }

  private ProblemDetail buildUnauthorizedProblem(HttpServletRequest request) {
    return ApiProblemDetailsFactory.create(
        HttpStatus.UNAUTHORIZED,
        "unauthorized",
        "Unauthorized",
        "Authentication is required or token is invalid.",
        request,
        "UNAUTHORIZED",
        List.of());
  }
}
