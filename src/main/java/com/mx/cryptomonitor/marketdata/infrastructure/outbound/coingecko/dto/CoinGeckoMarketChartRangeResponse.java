package com.mx.cryptomonitor.marketdata.infrastructure.outbound.coingecko.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinGeckoMarketChartRangeResponse(List<List<BigDecimal>> prices) {}
