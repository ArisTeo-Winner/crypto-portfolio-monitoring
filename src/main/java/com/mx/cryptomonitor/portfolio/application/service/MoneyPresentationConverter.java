package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.UsdMxnRateDetail;
import com.mx.cryptomonitor.portfolio.application.dto.response.MoneyPresentation;
import com.mx.cryptomonitor.portfolio.domain.model.PresentationCurrency;

import lombok.RequiredArgsConstructor;

/**
 * Convierte importes desde la moneda base (USD) a la moneda de presentacion elegida por el usuario
 * (ADR-0010). No toca el almacenamiento ni el P&L base; solo formato de salida. v1: USD->MXN con la
 * tasa actual ({@link FxRatePort}). Sin tasa disponible, cae a USD (no inventa). Los porcentajes
 * son invariantes de moneda y NO deben pasar por aqui.
 */
@Component
@RequiredArgsConstructor
public class MoneyPresentationConverter {

  private static final int MONEY_SCALE = 2;
  private static final String BASE_CURRENCY = "USD";

  private final FxRatePort fxRatePort;

  /** Resuelve moneda mostrada, tasa USD->display y su procedencia para el objetivo pedido. */
  public PresentationContext resolve(PresentationCurrency target) {
    if (target == PresentationCurrency.MXN) {
      Optional<UsdMxnRateDetail> detail =
          fxRatePort.usdMxnRateDetail().filter(d -> d.rate() != null && d.rate().signum() > 0);
      if (detail.isPresent()) {
        UsdMxnRateDetail d = detail.get();
        return new PresentationContext(PresentationCurrency.MXN, d.rate(), d.provider(), d.asOf());
      }
    }
    return new PresentationContext(PresentationCurrency.USD, BigDecimal.ONE, null, null);
  }

  /** Convierte un importe base USD a la moneda del contexto (redondeo a 2 decimales). */
  public BigDecimal toDisplay(BigDecimal usdAmount, PresentationContext context) {
    if (usdAmount == null) {
      return null;
    }
    if (context.currency() == PresentationCurrency.USD) {
      return usdAmount;
    }
    return usdAmount.multiply(context.rate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  public MoneyPresentation toEnvelope(PresentationContext context) {
    return new MoneyPresentation(
        BASE_CURRENCY,
        context.currency().name(),
        context.rate(),
        context.rateProvider(),
        context.rateAsOf());
  }

  /** Moneda mostrada + tasa USD->display + procedencia, resueltas una sola vez por request. */
  public record PresentationContext(
      PresentationCurrency currency,
      BigDecimal rate,
      String rateProvider,
      OffsetDateTime rateAsOf) {}
}
