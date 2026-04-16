package com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CoinMarketCapClientException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final CmcErrorType type;
  private final int httpStatus;
  private final Integer providerErrorCode;
  private final String providerErrorMessage;
  private final String rawBody;

  public CoinMarketCapClientException(
      String message,
      CmcErrorType type,
      int httpStatus,
      Integer providerErrorCode,
      String providerErrorMessage,
      String rawBody) {
    super(message);
    this.type = type;
    this.httpStatus = httpStatus;
    this.providerErrorCode = providerErrorCode;
    this.providerErrorMessage = providerErrorMessage;
    this.rawBody = rawBody;
  }
}
