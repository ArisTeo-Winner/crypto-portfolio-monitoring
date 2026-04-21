package com.mx.cryptomonitor.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.mx.cryptomonitor.user.application.port.out.AuditLogPersistencePort;
import com.mx.cryptomonitor.user.domain.model.AuditEventType;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

  @Mock private AuditLogPersistencePort auditLogPersistencePort;
  @Mock private HttpServletRequest request;

  @InjectMocks private AuditLogService auditLogService;

  @AfterEach
  void cleanupRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void logWithExplicitRequestShouldDelegateExpectedAuditValues() {
    UUID userId = UUID.randomUUID();
    when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
    when(request.getHeader("User-Agent")).thenReturn("JUnit");

    auditLogService.log(
        AuditEventType.AUTH_LOGIN_SUCCESS, "Authentication succeeded", userId, request);

    ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userAgentCaptor = ArgumentCaptor.forClass(String.class);

    verify(auditLogPersistencePort)
        .persist(
            org.mockito.ArgumentMatchers.eq(AuditEventType.AUTH_LOGIN_SUCCESS),
            descriptionCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(userId),
            ipCaptor.capture(),
            userAgentCaptor.capture());

    assertThat(descriptionCaptor.getValue()).isEqualTo("Authentication succeeded");
    assertThat(ipCaptor.getValue()).isEqualTo("203.0.113.5");
    assertThat(userAgentCaptor.getValue()).isEqualTo("JUnit");
  }

  @Test
  void logWithRequestContextShouldResolveRemoteAddress() {
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getHeader("X-Real-IP")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getHeader("User-Agent")).thenReturn("JUnit-UA");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    auditLogService.log(AuditEventType.TOKEN_REFRESH_FAILED, "Refresh failed", null);

    verify(auditLogPersistencePort)
        .persist(
            AuditEventType.TOKEN_REFRESH_FAILED, "Refresh failed", null, "127.0.0.1", "JUnit-UA");
  }
}
