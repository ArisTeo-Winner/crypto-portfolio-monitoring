package com.mx.cryptomonitor.asset.domain.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset_catalog")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetCatalogEntity {

  @Id
  @Column(name = "symbol", length = 20)
  private String symbol;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "asset_type", length = 20, nullable = false)
  private String assetType;

  @Column(name = "logo_url", length = 500)
  private String logoUrl;

  @Column(name = "exchange", length = 20)
  private String exchange;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "market_cap")
  private Long marketCap;

  // Field named 'popular'; Lombok @Data generates isPopular() getter for boolean fields.
  @Column(name = "is_popular", nullable = false)
  private boolean popular;

  // Estado de resolucion del logo: RESOLVED (hay URL) | NONE (confirmado sin logo) | PENDING.
  // NONE actua como cache de negativos: el pull-once no vuelve a intentar resolverlo.
  @Column(name = "logo_status", length = 20)
  private String logoStatus;

  @Column(name = "logo_checked_at")
  private OffsetDateTime logoCheckedAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
