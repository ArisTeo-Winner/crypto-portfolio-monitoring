package com.mx.cryptomonitor.tax.domain.service;

import java.util.Locale;

import com.mx.cryptomonitor.tax.domain.model.MarketJurisdiction;
import com.mx.cryptomonitor.tax.domain.model.TaxRegime;

/**
 * Rutea un holding a su régimen fiscal (ADR-0017 F1, acción 2). La jurisdicción determina el
 * régimen de las acciones: bolsa mexicana (BMV/SIC) → {@link TaxRegime#MX_BOLSA} (costo promedio,
 * Art. 129); mercado US → {@link TaxRegime#US_EQUITY} (FIFO).
 *
 * <p>En esta fase la jurisdicción se infiere de la <b>moneda</b> de la operación (MXN→MX, USD→US):
 * una acción comprada en pesos se opera en BMV/SIC y tributa como bolsa mexicana —el caso MELI—,
 * mientras que una comprada en dólares es mercado US. El refinamiento por catálogo/exchange
 * ([[ADR-0008]]) llega en una fase posterior; cripto, bonos y dividendos tienen régimen propio
 * (F2/F3) y aquí solo se clasifican.
 */
public class TaxRegimeResolver {

  public MarketJurisdiction jurisdictionOf(String currency) {
    if (currency == null || currency.isBlank()) {
      return MarketJurisdiction.OTHER;
    }
    return switch (currency.trim().toUpperCase(Locale.ROOT)) {
      case "MXN" -> MarketJurisdiction.MX;
      case "USD" -> MarketJurisdiction.US;
      default -> MarketJurisdiction.OTHER;
    };
  }

  public TaxRegime resolve(String assetType, String currency) {
    String type = assetType == null ? "" : assetType.trim().toUpperCase(Locale.ROOT);
    return switch (type) {
      case "STOCK", "ETF" -> equityRegime(jurisdictionOf(currency));
      case "CRYPTO" -> TaxRegime.CRYPTO;
      case "GOVERNMENT_BOND", "CORPORATE_BOND" -> TaxRegime.INTEREST;
      default -> TaxRegime.UNSUPPORTED;
    };
  }

  private TaxRegime equityRegime(MarketJurisdiction jurisdiction) {
    return switch (jurisdiction) {
      case MX -> TaxRegime.MX_BOLSA;
      case US -> TaxRegime.US_EQUITY;
      case OTHER -> TaxRegime.UNSUPPORTED;
    };
  }
}
