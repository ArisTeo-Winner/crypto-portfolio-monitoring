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
@Table(name = "market_price_snapshot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPriceSnapshotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "asset_symbol", nullable = false, length = 20)
  private String assetSymbol;

  @Column(name = "exchange", nullable = false, length = 20)
  private String exchange;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "price_close", nullable = false, precision = 18, scale = 4)
  private BigDecimal priceClose;

  @Column(name = "price_open", precision = 18, scale = 4)
  private BigDecimal priceOpen;

  @Column(name = "price_high", precision = 18, scale = 4)
  private BigDecimal priceHigh;

  @Column(name = "price_low", precision = 18, scale = 4)
  private BigDecimal priceLow;

  @Column(name = "price_avg", precision = 18, scale = 4)
  private BigDecimal priceAvg;

  @Column(name = "price_change", precision = 18, scale = 4)
  private BigDecimal priceChange;

  @Column(name = "pct_change", precision = 8, scale = 4)
  private BigDecimal pctChange;

  @Column(name = "volume")
  private Long volume;

  @Column(name = "importe_operado", precision = 20, scale = 2)
  private BigDecimal importeOperado;

  @Column(name = "provider", nullable = false, length = 20)
  private String provider;

  @Column(name = "quote_timestamp", nullable = false)
  private OffsetDateTime quoteTimestamp;

  @Builder.Default
  @Column(name = "cached_at", nullable = false)
  private OffsetDateTime cachedAt = OffsetDateTime.now(ZoneOffset.UTC);
}
