package com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc;

import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.dto.CmcStatus;

public record CmcErrorWrapper(CmcStatus status) {}
