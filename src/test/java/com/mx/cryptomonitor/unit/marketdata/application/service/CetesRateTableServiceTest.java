package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.dto.response.CetesRateTableEntry;
import com.mx.cryptomonitor.marketdata.application.service.BanxicoCurveService;
import com.mx.cryptomonitor.marketdata.application.service.CetesRateTableService;

@ExtendWith(MockitoExtension.class)
class CetesRateTableServiceTest {

  @Mock private BanxicoCurveService banxicoCurveService;

  @InjectMocks private CetesRateTableService service;

  @Test
  void getCetesTableMapsAllFiveTermsFromTheBanxicoCurve() {
    Map<Integer, BigDecimal> curve =
        Map.of(
            28, new BigDecimal("6.18"),
            91, new BigDecimal("6.49"),
            182, new BigDecimal("6.75"),
            364, new BigDecimal("6.93"),
            728, new BigDecimal("7.94"));
    when(banxicoCurveService.getCurve()).thenReturn(curve);
    when(banxicoCurveService.getAuctionDate()).thenReturn(Optional.of(LocalDate.of(2026, 7, 23)));

    List<CetesRateTableEntry> table = service.getCetesTable();

    assertThat(table).hasSize(5);
    assertThat(table)
        .extracting(CetesRateTableEntry::plazoDias)
        .containsExactly(28, 91, 182, 364, 728);
    assertThat(table)
        .extracting(CetesRateTableEntry::plazoLabel)
        .containsExactly("1 mes", "3 meses", "6 meses", "1 año", "2 años");
  }

  @Test
  void getCetesTableComputesCuponCeroPriceMatchingCetesdirectoFor28Days() {
    // 10 / (1 + 6.18/100 * 28/360) = 9.9521... -> 9.95, igual que la tabla publica de cetesdirecto.
    when(banxicoCurveService.getCurve()).thenReturn(Map.of(28, new BigDecimal("6.18")));
    when(banxicoCurveService.getAuctionDate()).thenReturn(Optional.of(LocalDate.of(2026, 7, 23)));

    List<CetesRateTableEntry> table = service.getCetesTable();

    assertThat(table).hasSize(1);
    CetesRateTableEntry entry = table.get(0);
    assertThat(entry.plazoDias()).isEqualTo(28);
    assertThat(entry.plazoLabel()).isEqualTo("1 mes");
    assertThat(entry.precio()).isEqualByComparingTo("9.95");
    assertThat(entry.tasa()).isEqualByComparingTo("6.1800");
    assertThat(entry.fecha()).isEqualTo(LocalDate.of(2026, 7, 23));
  }

  @Test
  void getCetesTableIsSortedByPlazoDiasAscendingRegardlessOfMapOrder() {
    Map<Integer, BigDecimal> curve =
        Map.of(
            364, new BigDecimal("6.93"),
            28, new BigDecimal("6.18"),
            182, new BigDecimal("6.75"),
            91, new BigDecimal("6.49"));
    when(banxicoCurveService.getCurve()).thenReturn(curve);
    when(banxicoCurveService.getAuctionDate()).thenReturn(Optional.of(LocalDate.of(2026, 7, 23)));

    List<CetesRateTableEntry> table = service.getCetesTable();

    assertThat(table).extracting(CetesRateTableEntry::plazoDias).containsExactly(28, 91, 182, 364);
  }

  @Test
  void getCetesTableFallsBackToTodayWhenAuctionDateIsUnavailable() {
    when(banxicoCurveService.getCurve()).thenReturn(Map.of(28, new BigDecimal("6.18")));
    when(banxicoCurveService.getAuctionDate()).thenReturn(Optional.empty());

    List<CetesRateTableEntry> table = service.getCetesTable();

    assertThat(table.get(0).fecha()).isEqualTo(LocalDate.now());
  }

  @Test
  void getCetesTableReturnsEmptyListWhenCurveIsUnavailable() {
    when(banxicoCurveService.getCurve()).thenReturn(Map.of());

    assertThat(service.getCetesTable()).isEmpty();
  }
}
