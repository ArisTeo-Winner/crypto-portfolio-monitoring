package com.mx.cryptomonitor.unit.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.transaction.domain.model.OperationTimestamps;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;

class OperationTimestampsTest {

  @Test
  void resolvesNewYorkSummerAsEdtMinusFour() {
    // 24-jun: America/New_York esta en EDT (UTC-4) -> 11:27:29 local = 15:27:29Z.
    OffsetDateTime utc =
        OperationTimestamps.resolveUtc(
            LocalDateTime.of(2025, 6, 24, 11, 27, 29), "America/New_York");

    assertThat(utc).isEqualTo(OffsetDateTime.parse("2025-06-24T15:27:29Z"));
  }

  @Test
  void resolvesNewYorkWinterAsEstMinusFive() {
    // 15-ene: America/New_York esta en EST (UTC-5) -> 11:27:29 local = 16:27:29Z.
    // Mismo nombre de zona, offset distinto segun la fecha: eso es lo que el binomio preserva.
    OffsetDateTime utc =
        OperationTimestamps.resolveUtc(
            LocalDateTime.of(2025, 1, 15, 11, 27, 29), "America/New_York");

    assertThat(utc).isEqualTo(OffsetDateTime.parse("2025-01-15T16:27:29Z"));
  }

  @Test
  void resolvesMexicoCityAsFixedMinusSixAllYear() {
    // America/Mexico_City es UTC-6 fijo tras abolir el horario de verano -> 00:00 = 06:00Z.
    OffsetDateTime utc =
        OperationTimestamps.resolveUtc(
            LocalDateTime.of(2025, 6, 24, 0, 0, 0), "America/Mexico_City");

    assertThat(utc).isEqualTo(OffsetDateTime.parse("2025-06-24T06:00:00Z"));
  }

  @Test
  void resolvesUtcZoneAsPassthrough() {
    OffsetDateTime utc =
        OperationTimestamps.resolveUtc(LocalDateTime.of(2026, 10, 12, 3, 54, 0), "UTC");

    assertThat(utc).isEqualTo(OffsetDateTime.parse("2026-10-12T03:54:00Z"));
  }

  @Test
  void returnsNullWhenBinomialIncomplete() {
    assertThat(OperationTimestamps.resolveUtc(null, "UTC")).isNull();
    assertThat(OperationTimestamps.resolveUtc(LocalDateTime.of(2025, 6, 24, 11, 0), null)).isNull();
    assertThat(OperationTimestamps.resolveUtc(LocalDateTime.of(2025, 6, 24, 11, 0), "  ")).isNull();
  }

  @Test
  void applyFillsBinomialAndDerivedInstantConsistently() {
    Transaction tx = new Transaction();

    OperationTimestamps.apply(tx, LocalDateTime.of(2025, 6, 24, 11, 27, 29), "America/New_York");

    assertThat(tx.getLocalExecutionTime()).isEqualTo(LocalDateTime.of(2025, 6, 24, 11, 27, 29));
    assertThat(tx.getExecutionZoneId()).isEqualTo("America/New_York");
    assertThat(tx.getExecutedAtUtc()).isEqualTo(OffsetDateTime.parse("2025-06-24T15:27:29Z"));
  }
}
