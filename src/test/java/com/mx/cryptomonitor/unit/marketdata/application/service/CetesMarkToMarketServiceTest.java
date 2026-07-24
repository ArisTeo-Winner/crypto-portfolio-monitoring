package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.application.dto.response.MarkToMarketResponse;
import com.mx.cryptomonitor.marketdata.application.port.out.GovBondPositionLookupPort;
import com.mx.cryptomonitor.marketdata.application.port.out.GovBondPositionView;
import com.mx.cryptomonitor.marketdata.application.service.BanxicoCurveService;
import com.mx.cryptomonitor.marketdata.application.service.CetesMarkToMarketService;
import com.mx.cryptomonitor.marketdata.domain.exception.GovBondPositionNotFoundException;
import com.mx.cryptomonitor.marketdata.domain.exception.InvalidGovBondPositionException;

class CetesMarkToMarketServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 7, 23);
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID TRANSACTION_ID = UUID.randomUUID();

  private GovBondPositionLookupPort positionLookupPort;
  private BanxicoCurveService curveService;
  private CetesMarkToMarketService service;

  @BeforeEach
  void setUp() {
    positionLookupPort = mock(GovBondPositionLookupPort.class);
    curveService = mock(BanxicoCurveService.class);
    Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    service = new CetesMarkToMarketService(positionLookupPort, curveService, fixedClock);
  }

  @Test
  void throwsNotFoundWhenPositionDoesNotBelongToUser() {
    when(positionLookupPort.findByTransactionIdAndUserId(TRANSACTION_ID, USER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMarkToMarket(USER_ID, TRANSACTION_ID))
        .isInstanceOf(GovBondPositionNotFoundException.class);
  }

  @Test
  void throwsInvalidWhenAssetTypeIsNotGovernmentBond() {
    mockPosition(position("STOCK", "MXN", TODAY.plusDays(30), 1000, 900, 8, 1000));

    assertThatThrownBy(() -> service.getMarkToMarket(USER_ID, TRANSACTION_ID))
        .isInstanceOf(InvalidGovBondPositionException.class);
  }

  @Test
  void throwsInvalidWhenCurrencyIsNotMxn() {
    mockPosition(position("GOVERNMENT_BOND", "USD", TODAY.plusDays(30), 1000, 900, 8, 1000));

    assertThatThrownBy(() -> service.getMarkToMarket(USER_ID, TRANSACTION_ID))
        .isInstanceOf(InvalidGovBondPositionException.class);
  }

  /**
   * precioHoy = 10 / (1 + 8%/100 * 180/360) = 10 / 1.04 = 9.615384... ; valorHoy = *100 titulos.
   */
  @Test
  void computesMarkToMarketWithTheExactCuponCeroFormula() {
    mockPosition(position("GOVERNMENT_BOND", "MXN", TODAY.plusDays(180), 100, 900, 7.5, 1000));
    when(curveService.getCurve()).thenReturn(Map.of(182, new BigDecimal("8")));

    MarkToMarketResponse response = service.getMarkToMarket(USER_ID, TRANSACTION_ID);

    assertThat(response.diasRestantes()).isEqualTo(180);
    assertThat(response.plazoSerieUsada()).isEqualTo(182);
    assertThat(response.tasaHoy()).isEqualByComparingTo("8.0000");
    assertThat(response.valorHoy()).isEqualByComparingTo("961.54");
    assertThat(response.mtmPnl()).isEqualByComparingTo("61.54");
    assertThat(response.mtmPnlPct()).isEqualByComparingTo("6.84");
    assertThat(response.valorAlVencimiento()).isEqualByComparingTo("1000.00");
    assertThat(response.vencida()).isFalse();
  }

  @Test
  void ratesDroppingSincePurchaseProduceAPositivePnl() {
    // Comprado a tasa 11.38%, hoy la tasa es 6.49% para el mismo plazo remanente: el precio de un
    // bono cupon cero sube cuando la tasa baja, asi que valorHoy debe superar a valorCompra.
    mockPosition(
        position("GOVERNMENT_BOND", "MXN", TODAY.plusDays(45), 1000, 9859.77, 11.38, 10000));
    when(curveService.getCurve()).thenReturn(Map.of(28, new BigDecimal("6.49")));

    MarkToMarketResponse response = service.getMarkToMarket(USER_ID, TRANSACTION_ID);

    assertThat(response.mtmPnl()).isGreaterThan(BigDecimal.ZERO);
    assertThat(response.valorHoy()).isGreaterThan(new BigDecimal("9859.77"));
  }

  @Test
  void maturedPositionIsSettledAtFaceValueWithoutCallingBanxico() {
    mockPosition(
        position("GOVERNMENT_BOND", "MXN", TODAY.minusDays(5), 1798, 17000, 11.38, 17990.74));

    MarkToMarketResponse response = service.getMarkToMarket(USER_ID, TRANSACTION_ID);

    assertThat(response.vencida()).isTrue();
    assertThat(response.valorHoy()).isEqualByComparingTo("17990.74");
    assertThat(response.mtmPnl()).isEqualByComparingTo("990.74");
    assertThat(response.tasaHoy()).isNull();
    assertThat(response.plazoSerieUsada()).isNull();
    verify(curveService, never()).getCurve();
  }

  @Test
  void unavailableCurveDegradesGracefullyWithoutThrowing() {
    mockPosition(position("GOVERNMENT_BOND", "MXN", TODAY.plusDays(30), 1000, 900, 8, 1000));
    when(curveService.getCurve()).thenReturn(Map.of());

    MarkToMarketResponse response = service.getMarkToMarket(USER_ID, TRANSACTION_ID);

    assertThat(response.valorHoy()).isNull();
    assertThat(response.mtmPnl()).isNull();
    assertThat(response.mtmPnlPct()).isNull();
    assertThat(response.tasaHoy()).isNull();
    assertThat(response.plazoSerieUsada()).isNull();
    assertThat(response.vencida()).isFalse();
    assertThat(response.valorCompra()).isEqualByComparingTo("900.00");
  }

  private void mockPosition(GovBondPositionView position) {
    when(positionLookupPort.findByTransactionIdAndUserId(any(), any()))
        .thenReturn(Optional.of(position));
  }

  private GovBondPositionView position(
      String assetType,
      String currency,
      LocalDate maturityDate,
      double quantity,
      double totalValue,
      double couponRate,
      double faceValue) {
    return new GovBondPositionView(
        TRANSACTION_ID,
        "CETES91",
        assetType,
        currency,
        BigDecimal.valueOf(quantity),
        BigDecimal.valueOf(totalValue),
        BigDecimal.valueOf(couponRate),
        maturityDate,
        BigDecimal.valueOf(faceValue));
  }
}
