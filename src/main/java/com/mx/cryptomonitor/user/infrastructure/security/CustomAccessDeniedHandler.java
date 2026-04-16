package com.mx.cryptomonitor.user.infrastructure.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    ProblemDetail problem = buildForbiddenProblem(request);

    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }

  private ProblemDetail buildForbiddenProblem(HttpServletRequest request) {
    return ApiProblemDetailsFactory.create(
        HttpStatus.FORBIDDEN,
        "forbidden",
        "Forbidden",
        "You do not have permission to access this resource.",
        request,
        "FORBIDDEN",
        List.of());
  }
}
