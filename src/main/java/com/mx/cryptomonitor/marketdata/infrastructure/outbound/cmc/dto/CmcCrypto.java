package com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.dto;

import java.util.Map;

public record CmcCrypto(String symbol, Map<String, CmcQuote> quote) {}
