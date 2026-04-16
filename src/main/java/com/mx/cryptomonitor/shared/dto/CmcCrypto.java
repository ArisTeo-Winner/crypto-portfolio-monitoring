package com.mx.cryptomonitor.shared.dto;

import java.util.Map;

public record CmcCrypto(String symbol, Map<String, CmcQuote> quote) {}
