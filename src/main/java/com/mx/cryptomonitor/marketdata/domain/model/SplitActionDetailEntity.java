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
 * Detalle del subtipo SPLIT a nivel seguridad (ADR-0014 F2b), 1:1 con {@link CorporateActionEntity}
 * vía PK compartida (CTI). Reconduce el split de ADR-0011 a la base. {@code shareMultiplier} =
 * split_to/split_from (la cantidad se multiplica, el precio se divide). La VALUACION sigue leyendo
 * {@code stock_split} (ADR-0011); este detalle es para el timeline/eventos unificados.
 */
@Entity
@Table(name = "split_action_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SplitActionDetailEntity {

  @Id
  @Column(name = "corporate_action_id", nullable = false, updatable = false)
  private UUID corporateActionId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "corporate_action_id")
  private CorporateActionEntity corporateAction;

  @Column(name = "share_multiplier", nullable = false, precision = 18, scale = 10)
  private BigDecimal shareMultiplier;

  @Column(name = "split_from", precision = 18, scale = 6)
  private BigDecimal splitFrom;

  @Column(name = "split_to", precision = 18, scale = 6)
  private BigDecimal splitTo;

  @Column(name = "adjustment_type", length = 20)
  private String adjustmentType;
}
