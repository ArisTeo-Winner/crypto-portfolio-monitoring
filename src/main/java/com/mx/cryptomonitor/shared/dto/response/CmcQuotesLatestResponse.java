package com.mx.cryptomonitor.shared.dto.response;

import java.util.List;
import java.util.Map;

import com.mx.cryptomonitor.shared.dto.CmcCrypto;
import com.mx.cryptomonitor.shared.dto.CmcStatus;

public record CmcQuotesLatestResponse(CmcStatus status, Map<String, List<CmcCrypto>> data) {}
