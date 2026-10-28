package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
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

@Entity
@Table(name = "market_fx_snapshot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketFxSnapshotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "ticker", nullable = false, length = 10)
  private String ticker;

  @Column(name = "rate", nullable = false, precision = 10, scale = 4)
  private BigDecimal rate;

  @Column(name = "pct_change", precision = 8, scale = 4)
  private BigDecimal pctChange;

  @Column(name = "abs_change", precision = 10, scale = 4)
  private BigDecimal absChange;

  @Builder.Default
  @Column(name = "provider", nullable = false, length = 20)
  private String provider = "DATABURSATIL";

  @Column(name = "quote_at", nullable = false)
  private OffsetDateTime quoteAt;

  @Builder.Default
  @Column(name = "cached_at", nullable = false)
  private OffsetDateTime cachedAt = OffsetDateTime.now(ZoneOffset.UTC);
}
