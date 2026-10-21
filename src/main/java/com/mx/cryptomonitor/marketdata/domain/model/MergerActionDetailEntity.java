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
 * Detalle del subtipo MERGER a nivel seguridad (ADR-0014 F6b), 1:1 con {@link
 * CorporateActionEntity} vía PK compartida (CTI). El evento vive sobre el PREDECESOR; {@code
 * successorSymbol} es el ticker que recibe el tenedor. {@code exchangeRatio} = acciones de la
 * sucesora por acción del predecesor (null en ALL_CASH); {@code cashPerShare} = efectivo por acción
 * (null en ALL_STOCK). El efecto por usuario (carryover basis / PnL) es F6b-2.
 */
@Entity
@Table(name = "merger_action_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MergerActionDetailEntity {

  @Id
  @Column(name = "corporate_action_id", nullable = false, updatable = false)
  private UUID corporateActionId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "corporate_action_id")
  private CorporateActionEntity corporateAction;

  @Enumerated(EnumType.STRING)
  @Column(name = "merger_type", nullable = false, length = 20)
  private MergerType mergerType;

  @Column(name = "successor_symbol", length = 20)
  private String successorSymbol;

  @Column(name = "exchange_ratio", precision = 18, scale = 8)
  private BigDecimal exchangeRatio;

  @Column(name = "cash_per_share", precision = 18, scale = 8)
  private BigDecimal cashPerShare;

  @Column(name = "currency", length = 3)
  private String currency;
}
