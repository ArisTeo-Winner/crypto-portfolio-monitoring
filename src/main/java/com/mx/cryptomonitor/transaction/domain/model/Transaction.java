package com.mx.cryptomonitor.transaction.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mx.cryptomonitor.user.domain.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "transaction")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "transaction_id", nullable = false, updatable = false)
  private UUID transactionId;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "portfolio_entry_id", nullable = false)
  private UUID portfolioEntryId;

  @Column(name = "asset_symbol", nullable = false, length = 10)
  private String assetSymbol;

  @Column(name = "asset_type", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private AssetType assetType;

  @Column(name = "transaction_type", nullable = false, length = 10)
  private String transactionType;

  @Column(name = "transfer_type", length = 20)
  private String transferType;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
  private BigDecimal quantity;

  @Column(name = "price_per_unit", nullable = false, precision = 18, scale = 8)
  private BigDecimal pricePerUnit;

  @Column(name = "total_value", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalValue;

  @Column(name = "transaction_date")
  private LocalDateTime transactionDate = LocalDateTime.now();

  @Column(name = "fee", precision = 18, scale = 2)
  private BigDecimal fee = BigDecimal.ZERO;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt = LocalDateTime.now();

  @JsonCreator
  public Transaction(@JsonProperty("transactionDate") LocalDateTime transactionDate) {
    this.transactionDate = (transactionDate != null) ? transactionDate : LocalDateTime.now();
  }
}
