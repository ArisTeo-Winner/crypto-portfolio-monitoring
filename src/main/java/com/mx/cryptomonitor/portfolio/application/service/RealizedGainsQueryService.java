package com.mx.cryptomonitor.portfolio.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.port.in.RealizedGainsQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.engine.AcbEngine;
import com.mx.cryptomonitor.portfolio.domain.engine.FifoEngine;

import lombok.RequiredArgsConstructor;

/**
 * Expone las ganancias realizadas del usuario calculadas con el engine de costeo solicitado
 * (ADR-0017 F1). Reutiliza {@link AcbEngine}/{@link FifoEngine} —misma mecánica que {@link
 * RealizedPnLService}— pero conserva símbolo, moneda y fecha de cada venta para que {@code tax}
 * rute por régimen y agregue por año fiscal. Un engine por activo; el orden es por fecha de
 * operación.
 */
@Service
@RequiredArgsConstructor
public class RealizedGainsQueryService implements RealizedGainsQueryPort {

  private final TransactionHistoryPort transactionHistoryPort;

  @Override
  public List<RealizedGain> realizedGains(UUID userId, CostingMethod method) {
    List<PortfolioTransactionSnapshot> snapshots =
        transactionHistoryPort.getTransactionsByUser(userId);
    List<PortfolioTransactionSnapshot> ordered =
        snapshots.stream()
            .sorted(Comparator.comparing(PortfolioTransactionSnapshot::transactionDate))
            .toList();

    List<RealizedGain> gains = new ArrayList<>();
    if (method == CostingMethod.FIFO) {
      Map<String, FifoEngine> engines = new LinkedHashMap<>();
      for (PortfolioTransactionSnapshot tx : ordered) {
        FifoEngine engine = engines.computeIfAbsent(key(tx), ignored -> new FifoEngine());
        if (isBuy(tx)) {
          engine.buy(tx.quantity(), tx.pricePerUnit());
        } else if (isSell(tx)) {
          gains.add(gain(tx, engine.sell(tx.quantity(), tx.pricePerUnit(), tx.fee())));
        }
      }
    } else {
      Map<String, AcbEngine> engines = new LinkedHashMap<>();
      for (PortfolioTransactionSnapshot tx : ordered) {
        AcbEngine engine = engines.computeIfAbsent(key(tx), ignored -> new AcbEngine());
        if (isBuy(tx)) {
          engine.buy(tx.quantity(), tx.pricePerUnit(), tx.fee());
        } else if (isSell(tx)) {
          gains.add(gain(tx, engine.sell(tx.quantity(), tx.pricePerUnit(), tx.fee())));
        }
      }
    }
    return gains;
  }

  private RealizedGain gain(PortfolioTransactionSnapshot tx, java.math.BigDecimal realized) {
    return new RealizedGain(
        tx.assetSymbol(),
        tx.assetType(),
        tx.currency(),
        tx.transactionDate().toLocalDate(),
        realized);
  }

  private boolean isBuy(PortfolioTransactionSnapshot tx) {
    return "BUY".equalsIgnoreCase(tx.transactionType());
  }

  private boolean isSell(PortfolioTransactionSnapshot tx) {
    return "SELL".equalsIgnoreCase(tx.transactionType());
  }

  private String key(PortfolioTransactionSnapshot tx) {
    return tx.assetType() + ":" + tx.assetSymbol().toUpperCase();
  }
}
