package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Festivo/cierre bursátil de un mercado (ADR-0012). {@code earlyClose} null = cierre total del día;
 * no-null = medio día (cierre anticipado a esa hora local). Sembrado por Flyway; fuente de verdad.
 */
@Entity
@Table(name = "market_holiday")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketHolidayEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "market", nullable = false, length = 16)
  private String market;

  @Column(name = "holiday_date", nullable = false)
  private LocalDate holidayDate;

  @Column(name = "name", nullable = false, length = 80)
  private String name;

  @Column(name = "early_close")
  private LocalTime earlyClose;
}
