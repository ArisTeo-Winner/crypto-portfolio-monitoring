package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detalle del subtipo BANKRUPTCY a nivel seguridad (ADR-0014 F6d), 1:1 con {@link
 * CorporateActionEntity} vía PK compartida (CTI). {@code recoveryPerShare} = valor recuperado por
 * acción (normalmente 0 para la acción común). El efecto por usuario (realizar la pérdida + marcar
 * BANKRUPT) es F6d-2.
 */
@Entity
@Table(name = "bankruptcy_action_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankruptcyActionDetailEntity {

  @Id
  @Column(name = "corporate_action_id", nullable = false, updatable = false)
  private UUID corporateActionId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "corporate_action_id")
  private CorporateActionEntity corporateAction;

  @Column(name = "recovery_per_share", nullable = false, precision = 18, scale = 8)
  private BigDecimal recoveryPerShare;

  @Column(name = "currency", length = 3)
  private String currency;
}
