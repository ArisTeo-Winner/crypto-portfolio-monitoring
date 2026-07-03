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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "bmv_price_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"emisora_serie", "trade_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BmvPriceHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "emisora_serie", nullable = false, length = 20)
  private String emisoraSerie;

  @Column(name = "trade_date", nullable = false)
  private LocalDate tradeDate;

  @Column(name = "close_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal closePrice;

  @Column(name = "amount_traded", precision = 20, scale = 2)
  private BigDecimal amountTraded;

  @Builder.Default
  @Column(name = "provider", nullable = false, length = 20)
  private String provider = "DATABURSATIL";

  @Builder.Default
  @Column(name = "cached_at", nullable = false)
  private OffsetDateTime cachedAt = OffsetDateTime.now(ZoneOffset.UTC);
}
