package com.mx.cryptomonitor.asset.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.asset.application.port.in.SecurityStatusPort;
import com.mx.cryptomonitor.asset.domain.model.MarketStatus;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Transiciones de estado de seguridades en el catálogo (ADR-0014 F6c+). Best-effort. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityStatusService implements SecurityStatusPort {

  private final AssetCatalogRepository assetCatalogRepository;

  @Override
  @Transactional
  public void markDelisted(String symbol) {
    applyStatus(symbol, MarketStatus.DELISTED);
  }

  @Override
  @Transactional
  public void markBankrupt(String symbol) {
    applyStatus(symbol, MarketStatus.BANKRUPT);
  }

  private void applyStatus(String symbol, MarketStatus status) {
    if (!StringUtils.hasText(symbol)) {
      return;
    }
    String normalized = symbol.trim().toUpperCase();
    assetCatalogRepository
        .findById(normalized)
        .ifPresent(
            row -> {
              row.setMarketStatus(status.name());
              row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
              assetCatalogRepository.save(row);
              log.info("Seguridad {} marcada {}", normalized, status);
            });
  }
}
