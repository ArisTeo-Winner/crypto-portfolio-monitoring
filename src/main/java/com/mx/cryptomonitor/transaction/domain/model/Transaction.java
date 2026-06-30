package com.mx.cryptomonitor.transaction.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

  @Column(name = "total_value", nullable = false, precision = 18, scale = 8)
  private BigDecimal totalValue;

  @Column(name = "transaction_date")
  private OffsetDateTime transactionDate = OffsetDateTime.now(ZoneOffset.UTC);

  @Column(name = "fee", precision = 18, scale = 8)
  private BigDecimal fee = BigDecimal.ZERO;

  @Builder.Default
  @Column(name = "realized_pnl", precision = 18, scale = 8)
  private BigDecimal realizedPnl = BigDecimal.ZERO;

  @Column(name = "asset_name", length = 255)
  private String assetName;

  @Column(name = "exchange", length = 20)
  private String exchange;

  @Column(name = "broker", length = 50)
  private String broker;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "face_value", precision = 18, scale = 8)
  private BigDecimal faceValue;

  @Column(name = "maturity_date")
  private LocalDate maturityDate;

  @Column(name = "coupon_rate", precision = 8, scale = 4)
  private BigDecimal couponRate;

  @Column(name = "auto_reinvestment", nullable = false)
  private boolean autoReinvestment;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

  @JsonCreator
  public Transaction(@JsonProperty("transactionDate") OffsetDateTime transactionDate) {
    this.transactionDate =
        (transactionDate != null) ? transactionDate : OffsetDateTime.now(ZoneOffset.UTC);
  }
}
