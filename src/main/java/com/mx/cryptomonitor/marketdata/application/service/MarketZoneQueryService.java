package com.mx.cryptomonitor.marketdata.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.marketdata.domain.model.Market;

/** Implementacion de {@link MarketZoneQueryPort} sobre el enum de dominio {@link Market}. */
@Service
public class MarketZoneQueryService implements MarketZoneQueryPort {

  @Override
  public Optional<String> zoneIdFor(String currency, String exchange) {
    Market market = Market.fromCurrencyAndExchange(currency, exchange);
    return market == null ? Optional.empty() : Optional.of(market.zone().getId());
  }
}
