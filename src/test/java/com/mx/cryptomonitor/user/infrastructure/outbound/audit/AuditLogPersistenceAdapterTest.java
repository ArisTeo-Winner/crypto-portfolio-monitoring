package com.mx.cryptomonitor.user.infrastructure.outbound.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.user.domain.model.AuditEventType;
import com.mx.cryptomonitor.user.domain.model.AuditLog;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.AuditLogRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class AuditLogPersistenceAdapterTest {

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private EntityManager entityManager;
  @Mock private UserRepository userRepository;

  @InjectMocks private AuditLogPersistenceAdapter auditLogPersistenceAdapter;

  @Test
  void persistShouldSaveExpectedAuditEntry() {
    UUID userId = UUID.randomUUID();
    User userRef = new User();
    userRef.setId(userId);
    when(userRepository.existsById(userId)).thenReturn(true);
    when(entityManager.getReference(User.class, userId)).thenReturn(userRef);

    auditLogPersistenceAdapter.persist(
        AuditEventType.AUTH_LOGIN_SUCCESS,
        "Authentication succeeded",
        userId,
        "203.0.113.5",
        "JUnit");

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog saved = captor.getValue();
    assertThat(saved.getEventType()).isEqualTo(AuditEventType.AUTH_LOGIN_SUCCESS);
    assertThat(saved.getDescription()).isEqualTo("Authentication succeeded");
    assertThat(saved.getIpAddress()).isEqualTo("203.0.113.5");
    assertThat(saved.getUserAgent()).isEqualTo("JUnit");
    assertThat(saved.getEventTimestamp()).isNotNull();
    assertThat(saved.getUser()).isEqualTo(userRef);
  }

  @Test
  void persistShouldNotBreakMainFlowWhenAuditRepositoryFails() {
    when(auditLogRepository.save(any(AuditLog.class)))
        .thenThrow(new RuntimeException("db unavailable"));

    assertThatCode(
            () ->
                auditLogPersistenceAdapter.persist(
                    AuditEventType.AUTH_LOGOUT_FAILED, "Logout failed", null, "127.0.0.1", "JUnit"))
        .doesNotThrowAnyException();
  }

  @Test
  void persistShouldNotResolveUserReferenceWhenUserIdIsNull() {
    auditLogPersistenceAdapter.persist(
        AuditEventType.AUTH_LOGOUT_SUCCESS, "Logout completed", null, "127.0.0.1", "JUnit");

    verifyNoInteractions(entityManager);
  }

  @Test
  void persistShouldSkipUserReferenceWhenUserIsNotCommittedYet() {
    UUID userId = UUID.randomUUID();
    when(userRepository.existsById(userId)).thenReturn(false);

    auditLogPersistenceAdapter.persist(
        AuditEventType.USER_REGISTER_SUCCESS,
        "Registration completed",
        userId,
        "127.0.0.1",
        "JUnit");

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    assertThat(captor.getValue().getUser()).isNull();
    verifyNoInteractions(entityManager);
  }
}
