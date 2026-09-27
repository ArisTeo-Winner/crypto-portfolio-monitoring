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

/** Tipo de cambio FIX de un dia (fecha de determinacion). Inmutable una vez publicado. */
@Entity
@Table(name = "fx_rate_daily")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateDailyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "ticker", nullable = false, length = 10)
  private String ticker;

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  @Column(name = "rate", nullable = false, precision = 18, scale = 8)
  private BigDecimal rate;

  @Builder.Default
  @Column(name = "provider", nullable = false, length = 20)
  private String provider = "BANXICO";

  @Builder.Default
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
}
