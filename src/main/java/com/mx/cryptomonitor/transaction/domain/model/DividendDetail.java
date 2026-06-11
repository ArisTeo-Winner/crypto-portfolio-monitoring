package com.mx.cryptomonitor.transaction.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dividend_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false, unique = true)
  private Transaction transaction;

  @Column(name = "ex_dividend_date")
  private LocalDate exDividendDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "dividend_type", nullable = false, length = 10)
  private DividendType dividendType;

  @Column(name = "tax_withheld", precision = 18, scale = 8)
  private BigDecimal taxWithheld;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
