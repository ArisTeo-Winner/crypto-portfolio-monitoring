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
    name = "banxico_cetes_rate",
    uniqueConstraints = @UniqueConstraint(columnNames = {"term_days", "auction_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BanxicoCetesRateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "term_days", nullable = false)
  private Integer termDays;

  @Column(name = "rate", nullable = false, precision = 8, scale = 4)
  private BigDecimal rate;

  @Column(name = "auction_date", nullable = false)
  private LocalDate auctionDate;

  @Builder.Default
  @Column(name = "fetched_at", nullable = false)
  private OffsetDateTime fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);
}
