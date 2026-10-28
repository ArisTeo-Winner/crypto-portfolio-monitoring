package com.mx.cryptomonitor.asset.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.asset.application.port.in.SecuritySuccessionPort;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.model.MarketStatus;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registra la sucesión tras una fusión (ADR-0014 F6b-2). Best-effort: solo toca filas existentes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecuritySuccessionService implements SecuritySuccessionPort {

  private final AssetCatalogRepository assetCatalogRepository;

  @Override
  @Transactional
  public void recordMerger(String predecessorSymbol, String successorSymbol) {
    if (!StringUtils.hasText(predecessorSymbol)) {
      return;
    }
    String predecessor = predecessorSymbol.trim().toUpperCase();
    String successor =
        StringUtils.hasText(successorSymbol) ? successorSymbol.trim().toUpperCase() : null;

    AssetCatalogEntity predecessorRow = assetCatalogRepository.findById(predecessor).orElse(null);
    if (predecessorRow != null) {
      predecessorRow.setMarketStatus(MarketStatus.MUTED_BY_MERGER.name());
      predecessorRow.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      assetCatalogRepository.save(predecessorRow);
    }

    if (successor != null) {
      assetCatalogRepository
          .findById(successor)
          .ifPresent(
              successorRow -> {
                successorRow.setPredecessorSymbol(predecessor);
                if (predecessorRow != null
                    && StringUtils.hasText(predecessorRow.getCompositeFigi())) {
                  successorRow.setPredecessorFigi(predecessorRow.getCompositeFigi());
                }
                successorRow.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                assetCatalogRepository.save(successorRow);
              });
    }
    log.info("Sucesión registrada: {} -> {} (predecesor MUTED_BY_MERGER)", predecessor, successor);
  }
}
