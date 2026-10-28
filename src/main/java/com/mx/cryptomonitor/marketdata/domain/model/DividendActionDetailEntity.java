package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Detalle del subtipo DIVIDEND a nivel seguridad (ADR-0014 F3a), 1:1 con {@link
 * CorporateActionEntity} vía PK compartida (CTI). Guarda lo que declara la empresa (DPS, moneda,
 * frecuencia, tipo, DPS split-adjusted, factor de ajuste de precio). La retencion fiscal y el neto
 * por usuario viven en la transaccion DIVIDEND del usuario, no aqui.
 */
@Entity
@Table(name = "dividend_action_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendActionDetailEntity {

  @Id
  @Column(name = "corporate_action_id", nullable = false, updatable = false)
  private UUID corporateActionId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "corporate_action_id")
  private CorporateActionEntity corporateAction;

  @Column(name = "amount_per_share", nullable = false, precision = 18, scale = 8)
  private BigDecimal amountPerShare;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "frequency")
  private Short frequency;

  @Enumerated(EnumType.STRING)
  @Column(name = "distribution_type", nullable = false, length = 20)
  private DividendDistributionType distributionType;

  @Column(name = "split_adjusted_dps", precision = 18, scale = 8)
  private BigDecimal splitAdjustedDps;

  @Column(name = "price_adjustment_factor", precision = 12, scale = 8)
  private BigDecimal priceAdjustmentFactor;
}
