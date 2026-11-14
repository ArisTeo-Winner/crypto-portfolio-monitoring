package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado de elegibilidad de dividendos por símbolo (ADR-0025 F1). Una fila por {@code asset_symbol}
 * (normalizado, igual que {@code corporate_action.asset_symbol}). Lo fija {@code
 * DividendSyncService} tras cada descubrimiento: {@code PAYS} si el proveedor devolvió eventos,
 * {@code NONE} si respondió vacío. Un símbolo sin fila es {@code UNKNOWN} (aún no verificado).
 */
@Entity
@Table(name = "asset_dividend_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetDividendStatusEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "asset_symbol", nullable = false, length = 20)
  private String assetSymbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private DividendEligibilityStatus status;

  @Column(name = "checked_provider", length = 20)
  private String checkedProvider;

  @Column(name = "last_checked_at", nullable = false)
  private OffsetDateTime lastCheckedAt;
}
