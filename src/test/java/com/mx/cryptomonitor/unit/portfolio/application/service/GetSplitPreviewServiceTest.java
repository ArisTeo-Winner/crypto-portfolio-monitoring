package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitPort;
import com.mx.cryptomonitor.portfolio.application.dto.request.SplitPreviewRequest;
import com.mx.cryptomonitor.portfolio.application.dto.response.SplitPreviewResponse;
import com.mx.cryptomonitor.portfolio.application.service.GetSplitPreviewService;

@ExtendWith(MockitoExtension.class)
class GetSplitPreviewServiceTest {

  @Mock private StockSplitPort stockSplitPort;

  private GetSplitPreviewService service() {
    return new GetSplitPreviewService(stockSplitPort);
  }

  private SplitPreviewRequest request(String symbol, String date, String quantity, String price) {
    return new SplitPreviewRequest(
        symbol,
        OffsetDateTime.parse(date + "T12:00:00Z").withOffsetSameInstant(ZoneOffset.UTC),
        quantity == null ? null : new BigDecimal(quantity),
        price == null ? null : new BigDecimal(price));
  }

  @Test
  void detectsForwardSplitAndReturnsAdjustedAmounts() {
    when(stockSplitPort.splitsFor("NVDA"))
        .thenReturn(
            List.of(new StockSplitData("NVDA", LocalDate.of(2024, 6, 7), new BigDecimal("10"))));

    SplitPreviewResponse response = service().preview(request("NVDA", "2024-05-01", "10", "900"));

    assertThat(response.splitDetected()).isTrue();
    assertThat(response.splitType()).isEqualTo("FORWARD");
    assertThat(response.factor()).isEqualByComparingTo("10");
    assertThat(response.adjusted().quantity()).isEqualByComparingTo("100");
    assertThat(response.adjusted().pricePerUnit()).isEqualByComparingTo("90");
    assertThat(response.original().quantity()).isEqualByComparingTo("10");
    assertThat(response.splits()).hasSize(1);
    assertThat(response.splits().get(0).ratio()).isEqualTo("10-for-1");
    assertThat(response.splits().get(0).executionDate()).isEqualTo(LocalDate.of(2024, 6, 7));
    assertThat(response.note())
        .contains("10-for-1", "07/06/2024", "100", "90", "no cambio de valor");
  }

  @Test
  void detectsReverseSplitRatioText() {
    when(stockSplitPort.splitsFor("WETO"))
        .thenReturn(
            List.of(new StockSplitData("WETO", LocalDate.of(2026, 8, 3), new BigDecimal("0.01"))));

    SplitPreviewResponse response =
        service().preview(request("WETO", "2026-07-31", "377.13248", "0.10993"));

    assertThat(response.splitDetected()).isTrue();
    assertThat(response.splitType()).isEqualTo("REVERSE");
    assertThat(response.adjusted().quantity()).isEqualByComparingTo("3.7713248");
    assertThat(response.adjusted().pricePerUnit()).isEqualByComparingTo("10.993");
    assertThat(response.splits().get(0).ratio()).isEqualTo("1-for-100");
    assertThat(response.note()).contains("split inverso", "agrupo cada 100", "no cambio de valor");
  }

  @Test
  void noSplitWhenNoneExist() {
    when(stockSplitPort.splitsFor("AAPL")).thenReturn(List.of());

    SplitPreviewResponse response = service().preview(request("AAPL", "2024-05-01", "10", "150"));

    assertThat(response.splitDetected()).isFalse();
    assertThat(response.factor()).isEqualByComparingTo("1");
    assertThat(response.adjusted().quantity()).isEqualByComparingTo("10");
    assertThat(response.note()).isNull();
  }

  @Test
  void noAdjustmentForBuyExecutedOnOrAfterSplitDate() {
    when(stockSplitPort.splitsFor("NVDA"))
        .thenReturn(
            List.of(new StockSplitData("NVDA", LocalDate.of(2024, 6, 7), new BigDecimal("10"))));

    // Compra el mismo dia del split: tx_date NO es anterior (<) => sin ajuste.
    SplitPreviewResponse response = service().preview(request("NVDA", "2024-06-07", "10", "90"));

    assertThat(response.splitDetected()).isFalse();
  }

  @Test
  void reportsDetectionWithoutAmountsWhenQuantityAndPriceAreOmitted() {
    when(stockSplitPort.splitsFor("NVDA"))
        .thenReturn(
            List.of(new StockSplitData("NVDA", LocalDate.of(2024, 6, 7), new BigDecimal("10"))));

    SplitPreviewResponse response = service().preview(request("NVDA", "2024-05-01", null, null));

    assertThat(response.splitDetected()).isTrue();
    assertThat(response.adjusted().quantity()).isNull();
    assertThat(response.note()).contains("10-for-1").doesNotContain("Ajustaremos");
  }
}
