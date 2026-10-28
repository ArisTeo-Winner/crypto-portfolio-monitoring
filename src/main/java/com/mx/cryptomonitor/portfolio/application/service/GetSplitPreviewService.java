package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitPort;
import com.mx.cryptomonitor.portfolio.application.dto.request.SplitPreviewRequest;
import com.mx.cryptomonitor.portfolio.application.dto.response.SplitPreviewResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.SplitPreviewResponse.Amounts;
import com.mx.cryptomonitor.portfolio.application.dto.response.SplitPreviewResponse.AppliedSplit;
import com.mx.cryptomonitor.portfolio.application.port.in.GetSplitPreviewUseCase;

import lombok.RequiredArgsConstructor;

/**
 * Preview de ajuste por split (ADR-0011). Lee splits via {@link StockSplitPort} (Redis -> Postgres;
 * el feed externo solo alimenta Postgres), detecta los splits con {@code execution_date} posterior
 * a la fecha de la operacion (regla {@code tx_date < execution_date}) y, reusando {@link
 * SplitFactors}, calcula como se veria la operacion en terminos post-split. No persiste nada: la
 * transaccion se guarda cruda y la proyeccion aplica el factor en lectura.
 */
@Service
@RequiredArgsConstructor
public class GetSplitPreviewService implements GetSplitPreviewUseCase {

  private static final int COST_SCALE = 8;
  private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final StockSplitPort stockSplitPort;

  @Override
  public SplitPreviewResponse preview(SplitPreviewRequest request) {
    BigDecimal quantity = request.quantity();
    BigDecimal pricePerUnit = request.pricePerUnit();
    if (request.transactionDate() == null || request.symbol() == null) {
      return SplitPreviewResponse.none(quantity, pricePerUnit);
    }

    LocalDate txDate = request.transactionDate().toLocalDate();
    List<AppliedSplit> applied = new ArrayList<>();
    BigDecimal factor = BigDecimal.ONE;
    for (StockSplitData split : SplitFactors.splitsOf(stockSplitPort, request.symbol())) {
      if (split.executionDate() != null
          && split.shareMultiplier() != null
          && txDate.isBefore(split.executionDate())) {
        factor = factor.multiply(split.shareMultiplier());
        applied.add(
            new AppliedSplit(
                ratioText(split.shareMultiplier()),
                split.executionDate(),
                split.shareMultiplier()));
      }
    }

    Amounts original = new Amounts(quantity, pricePerUnit);
    if (factor.compareTo(BigDecimal.ONE) == 0) {
      return SplitPreviewResponse.none(quantity, pricePerUnit);
    }

    BigDecimal adjQuantity =
        quantity == null ? null : SplitFactors.adjustQuantity(quantity, factor);
    BigDecimal adjPrice =
        pricePerUnit == null ? null : SplitFactors.adjustPrice(pricePerUnit, factor, COST_SCALE);
    Amounts adjusted = new Amounts(adjQuantity, adjPrice);
    String splitType = factor.compareTo(BigDecimal.ONE) > 0 ? "FORWARD" : "REVERSE";
    return new SplitPreviewResponse(
        true,
        splitType,
        factor,
        applied,
        original,
        adjusted,
        buildNote(request.symbol(), splitType, applied, original, adjusted));
  }

  // Deriva "N-for-1" / "1-for-N" del shareMultiplier cuando es entero o reciproco entero; si no,
  // cae a "x<factor>". shareMultiplier = split_to/split_from (StockSplitData no lleva from/to).
  private String ratioText(BigDecimal multiplier) {
    if (isInteger(multiplier) && multiplier.compareTo(BigDecimal.ONE) > 0) {
      return multiplier.setScale(0, RoundingMode.UNNECESSARY).toPlainString() + "-for-1";
    }
    if (multiplier.signum() > 0 && multiplier.compareTo(BigDecimal.ONE) < 0) {
      BigDecimal reciprocal = BigDecimal.ONE.divide(multiplier, 10, RoundingMode.HALF_UP);
      if (isInteger(reciprocal)) {
        return "1-for-" + reciprocal.setScale(0, RoundingMode.HALF_UP).toPlainString();
      }
    }
    return "x" + multiplier.stripTrailingZeros().toPlainString();
  }

  private boolean isInteger(BigDecimal value) {
    return value.stripTrailingZeros().scale() <= 0;
  }

  // Copia en lenguaje llano para un inversor retail que no sabe que es un split: explica que paso,
  // muestra la equivalencia y —lo mas importante— reafirma que el valor invertido no cambia.
  private String buildNote(
      String symbol,
      String splitType,
      List<AppliedSplit> applied,
      Amounts original,
      Amounts adjusted) {
    StringBuilder note = new StringBuilder();
    if (applied.size() == 1) {
      AppliedSplit split = applied.get(0);
      String fecha = split.executionDate().format(DISPLAY_DATE);
      if ("FORWARD".equals(splitType)) {
        note.append(symbol)
            .append(" hizo un split ")
            .append(split.ratio())
            .append(" el ")
            .append(fecha)
            .append(" (la empresa dividio cada titulo en ")
            .append(countText(split.shareMultiplier(), false))
            .append("), despues de tu compra.");
      } else {
        note.append(symbol)
            .append(" hizo un split inverso el ")
            .append(fecha)
            .append(" (la empresa agrupo cada ")
            .append(countText(split.shareMultiplier(), true))
            .append(" titulos en 1), despues de tu compra.");
      }
    } else {
      note.append(symbol)
          .append(" tuvo ")
          .append(applied.size())
          .append(" splits despues de tu compra.");
    }

    if (adjusted.quantity() != null && adjusted.pricePerUnit() != null) {
      String verbo = "FORWARD".equals(splitType) ? "baja" : "sube";
      note.append(" Tus ")
          .append(plain(original.quantity()))
          .append(" titulos se muestran como ")
          .append(plain(adjusted.quantity()))
          .append(" y el precio por titulo ")
          .append(verbo)
          .append(" de ")
          .append(plain(original.pricePerUnit()))
          .append(" a ")
          .append(plain(adjusted.pricePerUnit()))
          .append('.');
    }
    note.append(
        " Tu inversion no cambio de valor: solo cambio la forma de contar los titulos, no su valor"
            + " total.");
    return note.toString();
  }

  // Numero "humano" del split: para forward, el multiplicador (10); para reverse, su reciproco
  // (100).
  private String countText(BigDecimal multiplier, boolean reverse) {
    BigDecimal n =
        reverse ? BigDecimal.ONE.divide(multiplier, 10, RoundingMode.HALF_UP) : multiplier;
    return n.stripTrailingZeros().toPlainString();
  }

  private String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
