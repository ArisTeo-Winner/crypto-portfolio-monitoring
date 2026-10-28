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
 * Detalle del subtipo SPINOFF a nivel seguridad (ADR-0014 F6e), 1:1 con {@link
 * CorporateActionEntity} vía PK compartida (CTI). El evento vive sobre la MATRIZ; {@code
 * spincoSymbol} es la entidad escindida que recibe el tenedor. {@code costBasisAllocationPct} =
 * fracción (0..1) del costo base que migra a la spinco (null = por determinar). El efecto por
 * usuario (reparto de base) es F6e-2.
 */
@Entity
@Table(name = "spinoff_action_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpinoffActionDetailEntity {

  @Id
  @Column(name = "corporate_action_id", nullable = false, updatable = false)
  private UUID corporateActionId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "corporate_action_id")
  private CorporateActionEntity corporateAction;

  @Column(name = "spinco_symbol", nullable = false, length = 20)
  private String spincoSymbol;

  @Column(name = "shares_per_parent_share", nullable = false, precision = 18, scale = 8)
  private BigDecimal sharesPerParentShare;

  @Column(name = "cost_basis_allocation_pct", precision = 9, scale = 8)
  private BigDecimal costBasisAllocationPct;

  @Column(name = "currency", length = 3)
  private String currency;
}
