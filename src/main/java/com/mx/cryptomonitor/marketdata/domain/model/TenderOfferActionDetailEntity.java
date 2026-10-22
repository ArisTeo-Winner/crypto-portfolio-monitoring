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
 * Detalle del subtipo TENDER_OFFER (OPA) a nivel seguridad (ADR-0014 F6c), 1:1 con {@link
 * CorporateActionEntity} vía PK compartida (CTI). {@code resultedInDelisting} marca si la oferta
 * derivó en baja de cotización (caso TRAXION). El efecto por usuario (cash-out electivo / muteo) es
 * F6c-2.
 */
@Entity
@Table(name = "tender_offer_action_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenderOfferActionDetailEntity {

  @Id
  @Column(name = "corporate_action_id", nullable = false, updatable = false)
  private UUID corporateActionId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "corporate_action_id")
  private CorporateActionEntity corporateAction;

  @Column(name = "offer_price", nullable = false, precision = 18, scale = 8)
  private BigDecimal offerPrice;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "offeror_symbol", length = 20)
  private String offerorSymbol;

  @Column(name = "resulted_in_delisting", nullable = false)
  private boolean resultedInDelisting;
}
