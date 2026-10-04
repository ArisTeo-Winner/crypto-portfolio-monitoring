package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Marcador de ultima sincronizacion de splits por ticker (ADR-0011): distingue "sin splits" de
 * "nunca consultado" y permite TTL de refetch.
 */
@Entity
@Table(name = "stock_split_sync")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSplitSyncEntity {

  @Id
  @Column(name = "ticker", nullable = false, length = 20)
  private String ticker;

  @Column(name = "synced_at", nullable = false)
  private OffsetDateTime syncedAt;
}
