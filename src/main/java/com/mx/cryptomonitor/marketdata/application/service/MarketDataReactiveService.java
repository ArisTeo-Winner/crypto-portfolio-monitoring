package com.mx.cryptomonitor.marketdata.application.service;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.domain.model.PriceQuote;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MarketDataReactiveService {

  private final AssetPricePort assetPricePort;

  public Mono<PriceQuote> getLatestCryptoUsdQuote(String symbol) {
    return assetPricePort.getCryptoPrice(symbol);
  }
}
