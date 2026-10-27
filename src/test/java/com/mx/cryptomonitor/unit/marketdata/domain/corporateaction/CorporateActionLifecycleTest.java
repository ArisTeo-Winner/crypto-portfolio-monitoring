package com.mx.cryptomonitor.unit.marketdata.domain.corporateaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.domain.corporateaction.CorporateActionLifecycle;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;

class CorporateActionLifecycleTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

  @Test
  void completedCuandoPaymentDateYaPaso() {
    assertThat(
            CorporateActionLifecycle.statusFor(
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 14), TODAY))
        .isEqualTo(CorporateActionStatus.COMPLETED);
  }

  @Test
  void pagoHoyMismoCuentaComoCompleted() {
    assertThat(CorporateActionLifecycle.statusFor(LocalDate.of(2026, 10, 20), TODAY, TODAY))
        .isEqualTo(CorporateActionStatus.COMPLETED);
  }

  @Test
  void effectiveCuandoExPasoPeroPagoFuturo() {
    assertThat(
            CorporateActionLifecycle.statusFor(
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 2), TODAY))
        .isEqualTo(CorporateActionStatus.EFFECTIVE);
  }

  @Test
  void effectiveCuandoExPasoYSinPaymentDate() {
    assertThat(CorporateActionLifecycle.statusFor(LocalDate.of(2026, 10, 20), null, TODAY))
        .isEqualTo(CorporateActionStatus.EFFECTIVE);
  }

  @Test
  void announcedCuandoExEsFuturo() {
    assertThat(
            CorporateActionLifecycle.statusFor(
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 8), TODAY))
        .isEqualTo(CorporateActionStatus.ANNOUNCED);
  }

  @Test
  void soloCompletedEstaCongelado() {
    assertThat(CorporateActionLifecycle.isFrozen(CorporateActionStatus.COMPLETED)).isTrue();
    assertThat(CorporateActionLifecycle.isFrozen(CorporateActionStatus.EFFECTIVE)).isFalse();
    assertThat(CorporateActionLifecycle.isFrozen(CorporateActionStatus.ANNOUNCED)).isFalse();
    assertThat(CorporateActionLifecycle.isFrozen(CorporateActionStatus.CANCELLED)).isFalse();
  }
}
