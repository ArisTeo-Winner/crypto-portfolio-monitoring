package com.mx.cryptomonitor.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(
    name = "portfolio_entry",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "asset_symbol"}))
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "portfolio_entry_id", nullable = false, updatable = false)
  private UUID portfolioEntryId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "asset_symbol", nullable = false, length = 10)
  private String assetSymbol;

  @Column(name = "asset_type", nullable = false, length = 20)
  private String assetType;

  @Column(name = "total_quantity", nullable = false, precision = 18, scale = 8)
  private BigDecimal totalQuantity = BigDecimal.ZERO;

  @Column(name = "total_invested", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalInvested = BigDecimal.ZERO;

  @Column(name = "average_price_per_unit", nullable = false, precision = 18, scale = 8)
  private BigDecimal averagePricePerUnit = BigDecimal.ZERO;

  @Column(name = "last_transaction_price", precision = 18, scale = 8)
  private BigDecimal lastTransactionPrice;

  @Column(name = "current_value", precision = 18, scale = 2)
  private BigDecimal currentValue;

  @Column(name = "total_profit_loss", precision = 18, scale = 2)
  private BigDecimal totalProfitLoss;

  @UpdateTimestamp
  @Column(name = "last_updated", nullable = false)
  private LocalDateTime lastUpdated;

  @Column(name = "created_at", nullable = false, updatable = false)
  private final LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt = LocalDateTime.now();

  @Version private Long version;
}
