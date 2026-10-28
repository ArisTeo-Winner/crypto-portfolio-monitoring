package com.mx.cryptomonitor.marketdata.application.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.SplitActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.SplitActionDetailRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconducción forward del SPLIT a la base CTI (ADR-0014 F2b): cuando se sincronizan splits nuevos,
 * los refleja en {@code corporate_action}(SPLIT) + {@code split_action_detail}, idempotente por la
 * llave natural (símbolo + SPLIT + fecha de ejecución). NO participa en la valuación (ADR-0011
 * sigue leyendo {@code stock_split}); solo mantiene la espina común al día para timeline/eventos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SplitReconductionService {

  private static final String PROVIDER = "MASSIVE";

  private final CorporateActionRepository corporateActionRepository;
  private final SplitActionDetailRepository splitDetailRepository;

  @Transactional
  public int reconduct(String assetSymbol, List<StockSplitData> splits) {
    if (!StringUtils.hasText(assetSymbol) || splits == null || splits.isEmpty()) {
      return 0;
    }
    String symbol = assetSymbol.trim().toUpperCase();
    int created = 0;
    for (StockSplitData split : splits) {
      if (split.executionDate() == null
          || corporateActionRepository.existsByAssetSymbolAndActionTypeAndPivotalDate(
              symbol, CorporateActionType.SPLIT, split.executionDate())) {
        continue;
      }
      CorporateActionEntity action =
          corporateActionRepository.save(toCorporateAction(symbol, split));
      splitDetailRepository.save(
          SplitActionDetailEntity.builder()
              .corporateAction(action)
              .shareMultiplier(split.shareMultiplier())
              .build());
      created++;
    }
    if (created > 0) {
      log.info("Reconducción SPLIT {}: {} evento(s) nuevo(s) a la base", symbol, created);
    }
    return created;
  }

  private CorporateActionEntity toCorporateAction(String symbol, StockSplitData split) {
    CorporateActionStatus status =
        split.executionDate().isAfter(LocalDate.now(ZoneOffset.UTC))
            ? CorporateActionStatus.ANNOUNCED
            : CorporateActionStatus.COMPLETED;
    return CorporateActionEntity.builder()
        .assetSymbol(symbol)
        .actionType(CorporateActionType.SPLIT)
        .status(status)
        .pivotalDate(split.executionDate())
        .sourceProvider(PROVIDER)
        .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
