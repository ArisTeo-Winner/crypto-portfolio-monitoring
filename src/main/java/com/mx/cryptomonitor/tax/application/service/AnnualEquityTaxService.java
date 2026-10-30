package com.mx.cryptomonitor.tax.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.port.in.RealizedGainsQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.RealizedGainsQueryPort.CostingMethod;
import com.mx.cryptomonitor.portfolio.application.port.in.RealizedGainsQueryPort.RealizedGain;
import com.mx.cryptomonitor.tax.application.port.in.CalculateAnnualEquityTaxUseCase;
import com.mx.cryptomonitor.tax.domain.model.AnnualRegimeTax;
import com.mx.cryptomonitor.tax.domain.model.TaxRegime;
import com.mx.cryptomonitor.tax.domain.model.TaxableSale;
import com.mx.cryptomonitor.tax.domain.service.EquityTaxAggregator;
import com.mx.cryptomonitor.tax.domain.service.TaxRegimeResolver;

import lombok.RequiredArgsConstructor;

/**
 * Orquesta el reporte fiscal anual de acciones (ADR-0017 F1): pide a portfolio las ganancias
 * realizadas con cada método de costeo, rutea cada venta a su régimen y delega la agregación al
 * {@link EquityTaxAggregator}. El costeo lo fija el régimen —bolsa MX usa costo promedio (ACB), US
 * usa FIFO—, que es precisamente lo que prescriben el Art. 129 y la constancia de GBM para bolsa.
 */
@Service
@RequiredArgsConstructor
public class AnnualEquityTaxService implements CalculateAnnualEquityTaxUseCase {

  private static final String CURRENCY = "MXN";
  private static final String DISCLAIMER =
      "Estimación de verificación, NO asesoría fiscal ni sustituto de la declaración. Tasas y"
          + " artículos (Art. 129 LISR, 10% cedular) a validar con un fiscalista. GBM ya retiene y"
          + " reporta bolsa: coteje este reporte contra su constancia anual.";

  private final RealizedGainsQueryPort realizedGainsQueryPort;
  private final TaxRegimeResolver regimeResolver = new TaxRegimeResolver();
  private final EquityTaxAggregator aggregator = new EquityTaxAggregator();

  @Override
  public AnnualEquityTaxReport calculate(UUID userId, int fiscalYear) {
    List<TaxableSale> sales = new ArrayList<>();
    collect(
        realizedGainsQueryPort.realizedGains(userId, CostingMethod.AVERAGE_COST),
        TaxRegime.MX_BOLSA,
        sales);
    collect(
        realizedGainsQueryPort.realizedGains(userId, CostingMethod.FIFO),
        TaxRegime.US_EQUITY,
        sales);

    List<RegimeTaxLine> lines =
        aggregator.aggregate(sales, fiscalYear).stream().map(this::toLine).toList();
    return new AnnualEquityTaxReport(fiscalYear, lines, DISCLAIMER);
  }

  private void collect(List<RealizedGain> gains, TaxRegime expected, List<TaxableSale> target) {
    for (RealizedGain gain : gains) {
      if (regimeResolver.resolve(gain.assetType(), gain.currency()) == expected) {
        target.add(
            new TaxableSale(
                expected, gain.assetSymbol(), gain.saleDate().getYear(), gain.realizedAmount()));
      }
    }
  }

  private RegimeTaxLine toLine(AnnualRegimeTax result) {
    return new RegimeTaxLine(
        result.regime().name(),
        CURRENCY,
        result.grossGain(),
        result.grossLoss(),
        result.netResult(),
        result.taxableBase(),
        result.lossCarryforward(),
        result.cedularRate(),
        result.estimatedTax());
  }
}
