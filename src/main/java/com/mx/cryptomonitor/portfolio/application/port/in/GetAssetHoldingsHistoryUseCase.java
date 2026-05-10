package com.mx.cryptomonitor.portfolio.application.port.in;

import java.util.UUID;

import com.mx.cryptomonitor.portfolio.application.dto.response.AssetHoldingsHistoryResponse;

public interface GetAssetHoldingsHistoryUseCase {

  AssetHoldingsHistoryResponse getAssetHoldingsHistory(UUID userId, String symbol, String range);
}
