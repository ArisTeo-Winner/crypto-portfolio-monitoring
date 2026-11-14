package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort.AssetDisplay;
import com.mx.cryptomonitor.transaction.application.dto.response.UserDividendResponse;
import com.mx.cryptomonitor.transaction.application.port.in.GetUserDividendsUseCase;
import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read-model de ingresos por dividendo del usuario (ADR-0029). Lee los dividendos ya recibidos
 * (fecha de pago &lt;= hoy) del usuario y los presenta como "Mis Dividendos", con nombre/logo del
 * catalogo. No recalcula nada: el accrual (ADR-0024/0027) ya dejo el monto por usuario.
 */
@Service
@RequiredArgsConstructor
public class UserDividendQueryService implements GetUserDividendsUseCase {

  private static final int SHARES_SCALE = 8;

  private final DividendDetailRepository dividendDetailRepository;
  private final AssetCatalogQueryPort assetCatalogQueryPort;

  @Override
  public List<UserDividendResponse> getUserDividends(UUID userId) {
    List<DividendDetail> details = dividendDetailRepository.findUserDividends(userId, now());
    if (details.isEmpty()) {
      return List.of();
    }
    Map<String, AssetDisplay> display =
        assetCatalogQueryPort.findDisplayBySymbols(
            details.stream()
                .map(d -> d.getTransaction().getAssetSymbol())
                .collect(Collectors.toSet()));
    return details.stream().map(d -> toResponse(d, display)).toList();
  }

  private UserDividendResponse toResponse(
      DividendDetail detail, Map<String, AssetDisplay> display) {
    Transaction tx = detail.getTransaction();
    String symbol = tx.getAssetSymbol();
    AssetDisplay d = symbol == null ? null : display.get(symbol.toUpperCase());

    BigDecimal gross = orZero(tx.getTotalValue());
    BigDecimal tax = orZero(detail.getTaxWithheld());
    BigDecimal amountPerShare = detail.getAmountPerShare();
    BigDecimal shares =
        (amountPerShare != null && amountPerShare.signum() > 0)
            ? gross.divide(amountPerShare, SHARES_SCALE, RoundingMode.HALF_UP)
            : null;

    return new UserDividendResponse(
        symbol,
        d != null ? d.name() : null,
        d != null ? d.logoUrl() : null,
        detail.getExDividendDate(),
        tx.getTransactionDate(),
        shares,
        amountPerShare,
        gross,
        tax,
        gross.subtract(tax),
        detail.getDividendType() == null ? null : detail.getDividendType().name(),
        tx.getCurrency());
  }

  private static BigDecimal orZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  protected OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }
}
