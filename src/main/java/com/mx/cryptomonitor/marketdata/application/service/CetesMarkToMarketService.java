package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.dto.response.MarkToMarketResponse;
import com.mx.cryptomonitor.marketdata.application.port.out.GovBondPositionLookupPort;
import com.mx.cryptomonitor.marketdata.application.port.out.GovBondPositionView;
import com.mx.cryptomonitor.marketdata.domain.exception.GovBondPositionNotFoundException;
import com.mx.cryptomonitor.marketdata.domain.exception.InvalidGovBondPositionException;

import lombok.RequiredArgsConstructor;

/**
 * Valuacion a mercado (mark-to-market) de una posicion CETES viva: descuento cupon cero con la tasa
 * vigente del plazo mas cercano a los dias restantes al vencimiento.
 */
@Service
@RequiredArgsConstructor
public class CetesMarkToMarketService {

  private static final String GOVERNMENT_BOND = "GOVERNMENT_BOND";
  private static final String MXN = "MXN";
  private static final BigDecimal FACE_VALUE_UNIT = BigDecimal.TEN;
  private static final BigDecimal DAY_COUNT_BASIS = BigDecimal.valueOf(360);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

  private final GovBondPositionLookupPort positionLookupPort;
  private final BanxicoCurveService curveService;
  private final Clock clock;

  public MarkToMarketResponse getMarkToMarket(UUID userId, UUID transactionId) {
    GovBondPositionView position = resolvePosition(userId, transactionId);

    LocalDate hoy = LocalDate.now(clock);
    long diasRestantes = ChronoUnit.DAYS.between(hoy, position.maturityDate());
    BigDecimal valorCompra = position.totalValue();
    BigDecimal valorAlVencimiento = scaleAmount(position.faceValue());
    BigDecimal tasaCompra = scaleRate(position.couponRate());

    if (diasRestantes <= 0) {
      BigDecimal mtmPnl = position.faceValue().subtract(valorCompra);
      return new MarkToMarketResponse(
          scaleAmount(valorCompra),
          scaleAmount(position.faceValue()),
          valorAlVencimiento,
          scaleAmount(mtmPnl),
          percentPnl(mtmPnl, valorCompra),
          tasaCompra,
          null,
          diasRestantes,
          null,
          hoy,
          true);
    }

    Optional<Map.Entry<Integer, BigDecimal>> nearest = nearestCurvePoint(diasRestantes);
    if (nearest.isEmpty()) {
      return new MarkToMarketResponse(
          scaleAmount(valorCompra),
          null,
          valorAlVencimiento,
          null,
          null,
          tasaCompra,
          null,
          diasRestantes,
          null,
          hoy,
          false);
    }

    int plazoSerieUsada = nearest.get().getKey();
    BigDecimal tasaHoy = nearest.get().getValue();
    BigDecimal valorHoy = descuentoCuponCero(tasaHoy, diasRestantes, position.quantity());
    BigDecimal mtmPnl = valorHoy.subtract(valorCompra);

    return new MarkToMarketResponse(
        scaleAmount(valorCompra),
        scaleAmount(valorHoy),
        valorAlVencimiento,
        scaleAmount(mtmPnl),
        percentPnl(mtmPnl, valorCompra),
        tasaCompra,
        scaleRate(tasaHoy),
        diasRestantes,
        plazoSerieUsada,
        hoy,
        false);
  }

  private GovBondPositionView resolvePosition(UUID userId, UUID transactionId) {
    GovBondPositionView position =
        positionLookupPort
            .findByTransactionIdAndUserId(transactionId, userId)
            .orElseThrow(
                () ->
                    new GovBondPositionNotFoundException(
                        "Transaccion no encontrada: " + transactionId));

    if (!GOVERNMENT_BOND.equals(position.assetType())) {
      throw new InvalidGovBondPositionException(
          "La transaccion " + transactionId + " no es un bono gubernamental");
    }
    if (!MXN.equals(position.currency())) {
      throw new InvalidGovBondPositionException(
          "La transaccion " + transactionId + " no esta denominada en MXN");
    }
    return position;
  }

  private Optional<Map.Entry<Integer, BigDecimal>> nearestCurvePoint(long diasRestantes) {
    Map<Integer, BigDecimal> curve = curveService.getCurve();
    if (curve.isEmpty()) {
      return Optional.empty();
    }
    int diasRestantesInt = (int) Math.min(diasRestantes, Integer.MAX_VALUE);
    return curve.entrySet().stream()
        .min(Comparator.comparingInt(entry -> Math.abs(entry.getKey() - diasRestantesInt)));
  }

  /**
   * precioHoyUnit = 10 / (1 + tasaHoy/100 * diasRestantes/360); valorHoy = precioHoyUnit *
   * numTitulos.
   */
  private BigDecimal descuentoCuponCero(
      BigDecimal tasaHoy, long diasRestantes, BigDecimal numTitulos) {
    BigDecimal tasaDecimal = tasaHoy.divide(HUNDRED, MC);
    BigDecimal factor =
        tasaDecimal.multiply(BigDecimal.valueOf(diasRestantes)).divide(DAY_COUNT_BASIS, MC);
    BigDecimal denominator = BigDecimal.ONE.add(factor);
    BigDecimal precioHoyUnit = FACE_VALUE_UNIT.divide(denominator, MC);
    return precioHoyUnit.multiply(numTitulos, MC);
  }

  private BigDecimal percentPnl(BigDecimal pnl, BigDecimal base) {
    if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return pnl.divide(base, MC).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal scaleAmount(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal scaleRate(BigDecimal value) {
    return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
  }
}
