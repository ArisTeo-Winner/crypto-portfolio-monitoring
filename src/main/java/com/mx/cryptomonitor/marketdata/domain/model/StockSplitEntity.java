package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Split de acciones de un ticker en una fecha (ADR-0011). Inmutable una vez publicado. */
@Entity
@Table(name = "stock_split")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSplitEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "ticker", nullable = false, length = 20)
  private String ticker;

  @Column(name = "execution_date", nullable = false)
  private LocalDate executionDate;

  @Column(name = "split_from", precision = 18, scale = 6)
  private BigDecimal splitFrom;

  @Column(name = "split_to", precision = 18, scale = 6)
  private BigDecimal splitTo;

  @Column(name = "share_multiplier", nullable = false, precision = 18, scale = 10)
  private BigDecimal shareMultiplier;

  @Column(name = "adjustment_type", length = 20)
  private String adjustmentType;

  @Builder.Default
  @Column(name = "provider", nullable = false, length = 20)
  private String provider = "MASSIVE";

  @Builder.Default
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
}
