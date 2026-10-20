package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base del framework de corporate actions (ADR-0014, Class Table Inheritance): la espina comun de
 * todo evento. El detalle especifico por tipo vive en tablas hijas (split_detail, dividend_detail,
 * merger_detail, ...) enlazadas a esta, o en {@code rawPayload} para la cola larga. Fechas = {@link
 * LocalDate} (evento de calendario, sin zona — distinto a la ejecucion de ADR-0013).
 */
@Entity
@Table(name = "corporate_action")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorporateActionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "asset_symbol", nullable = false, length = 20)
  private String assetSymbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 20)
  private CorporateActionType actionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 15)
  private CorporateActionStatus status;

  @Column(name = "pivotal_date", nullable = false)
  private LocalDate pivotalDate;

  @Column(name = "declaration_date")
  private LocalDate declarationDate;

  @Column(name = "record_date")
  private LocalDate recordDate;

  @Column(name = "payment_date")
  private LocalDate paymentDate;

  @Column(name = "source_provider", nullable = false, length = 20)
  private String sourceProvider;

  @Column(name = "provider_event_id", length = 80)
  private String providerEventId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_payload")
  private String rawPayload;

  @Column(name = "synced_at", nullable = false)
  private OffsetDateTime syncedAt;
}
