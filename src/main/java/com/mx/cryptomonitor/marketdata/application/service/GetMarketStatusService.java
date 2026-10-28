package com.mx.cryptomonitor.marketdata.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse;
import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse.ExtendedHours;
import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse.MarketStatusItem;
import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse.NextChange;
import com.mx.cryptomonitor.marketdata.application.dto.response.MarketStatusResponse.RegularHours;
import com.mx.cryptomonitor.marketdata.application.port.in.GetMarketStatusUseCase;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.MarketStatusSnapshot;

/** Ensambla el estado de ambos mercados a partir de {@link TradingCalendarService} (ADR-0012). */
@Service
public class GetMarketStatusService implements GetMarketStatusUseCase {

  private final TradingCalendarService calendar;
  private final Clock clock;

  @Autowired
  public GetMarketStatusService(TradingCalendarService calendar) {
    this(calendar, Clock.systemUTC());
  }

  public GetMarketStatusService(TradingCalendarService calendar, Clock clock) {
    this.calendar = calendar;
    this.clock = clock;
  }

  @Override
  public MarketStatusResponse currentStatus() {
    Instant now = Instant.now(clock);
    List<MarketStatusItem> markets =
        Arrays.stream(Market.values()).map(market -> toItem(market, now)).toList();
    return new MarketStatusResponse(now, markets);
  }

  private MarketStatusItem toItem(Market market, Instant now) {
    MarketStatusSnapshot s = calendar.statusAt(market, now);
    RegularHours regular =
        new RegularHours(hhmm(market.regularOpen()), hhmm(market.regularClose()));
    ExtendedHours extended =
        market.hasExtendedHours()
            ? new ExtendedHours(hhmm(market.preOpen()), hhmm(market.afterClose()))
            : null;
    NextChange nextChange = new NextChange(s.nextChangeType().name(), s.nextChangeAt());
    return new MarketStatusItem(
        market.code(),
        market.label(),
        market.exchange(),
        market.zone().getId(),
        s.phase().name(),
        s.isOpen(),
        regular,
        extended,
        nextChange,
        s.reasonCode().name());
  }

  private static String hhmm(LocalTime time) {
    return time.toString();
  }
}
