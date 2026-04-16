package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;

import com.mx.cryptomonitor.marketdata.domain.model.PriceQuote;

import reactor.core.publisher.Mono;

public interface AssetPricePort {
  Mono<PriceQuote> getCryptoPrice(String symbol);

  Mono<BigDecimal> getCryptoPriceAmount(String symbol);
}
