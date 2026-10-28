package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record CoinGeckoSearchResponse(List<CoinGeckoSearchCoin> coins) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record CoinGeckoSearchCoin(
    String id,
    String name,
    String symbol,
    @JsonProperty("market_cap_rank") Integer marketCapRank) {}
