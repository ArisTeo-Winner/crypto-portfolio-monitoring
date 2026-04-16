package com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.dto;

import java.util.List;
import java.util.Map;

public record CmcQuotesLatestResponse(CmcStatus status, Map<String, List<CmcCrypto>> data) {}
