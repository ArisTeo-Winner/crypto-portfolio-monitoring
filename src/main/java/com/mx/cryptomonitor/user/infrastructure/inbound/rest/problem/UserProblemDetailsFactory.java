package com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;

public final class UserProblemDetailsFactory {

  private UserProblemDetailsFactory() {}

  public static ProblemDetail create(
      HttpStatus status,
      String problemType,
      String title,
      String detail,
      HttpServletRequest request,
      String errorCode,
      List<String> errors) {
    return ApiProblemDetailsFactory.create(
        status, problemType, title, detail, request, errorCode, errors);
  }

  public static String resolveTraceId(HttpServletRequest request) {
    return ApiProblemDetailsFactory.resolveTraceId(request);
  }
}
