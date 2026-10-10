package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse;
import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse.MarketStatusItem;
import com.mx.cryptomonitor.marketdata.application.service.GetMarketStatusService;
import com.mx.cryptomonitor.marketdata.application.service.TradingCalendarService;
import com.mx.cryptomonitor.marketdata.domain.model.MarketHolidayEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketHolidayRepository;

@ExtendWith(MockitoExtension.class)
class GetMarketStatusServiceTest {

  @Mock private MarketHolidayRepository holidayRepository;

  @Test
  void buildsBothMarketsWithContractShape() {
    when(holidayRepository.findByMarket("US_EQUITY")).thenReturn(List.of());
    when(holidayRepository.findByMarket("MX_BMV")).thenReturn(List.<MarketHolidayEntity>of());
    // 2026-10-07 (miércoles) 15:00Z = 11:00 ET (NYSE abierto) y 09:00 CT (BMV abierto).
    Clock clock = Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneOffset.UTC);
    GetMarketStatusService service =
        new GetMarketStatusService(new TradingCalendarService(holidayRepository, clock), clock);

    MarketStatusResponse response = service.currentStatus();

    assertThat(response.asOf()).isEqualTo(Instant.parse("2026-10-07T15:00:00Z"));
    assertThat(response.markets()).hasSize(2);

    MarketStatusItem nyse = itemByCode(response, "NYSE");
    assertThat(nyse.label()).isEqualTo("Estados Unidos");
    assertThat(nyse.timezone()).isEqualTo("America/New_York");
    assertThat(nyse.phase()).isEqualTo("OPEN");
    assertThat(nyse.isOpen()).isTrue();
    assertThat(nyse.regular().open()).isEqualTo("09:30");
    assertThat(nyse.regular().close()).isEqualTo("16:00");
    assertThat(nyse.extended().pre()).isEqualTo("04:00");
    assertThat(nyse.extended().after()).isEqualTo("20:00");
    assertThat(nyse.nextChange().type()).isEqualTo("CLOSE");

    MarketStatusItem bmv = itemByCode(response, "BMV");
    assertThat(bmv.timezone()).isEqualTo("America/Mexico_City");
    assertThat(bmv.phase()).isEqualTo("OPEN");
    assertThat(bmv.regular().open()).isEqualTo("08:30");
    assertThat(bmv.extended()).isNull(); // BMV no tiene horas extendidas
  }

  private MarketStatusItem itemByCode(MarketStatusResponse response, String code) {
    return response.markets().stream().filter(m -> m.code().equals(code)).findFirst().orElseThrow();
  }
}
