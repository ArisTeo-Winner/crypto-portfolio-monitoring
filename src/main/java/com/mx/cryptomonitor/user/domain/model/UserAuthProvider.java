package com.mx.cryptomonitor.user.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "user_auth_providers",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_user_provider",
          columnNames = {"user_id", "auth_provider"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAuthProvider {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_provider", nullable = false, length = 32)
  private AuthProvider authProvider;

  @Column(name = "provider_id", length = 191)
  private String providerId;

  @Column(name = "provider_email", length = 191)
  private String providerEmail;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime lastLogin;
}
