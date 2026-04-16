package com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc;

public enum CmcErrorType {
  BAD_REQUEST,
  UNAUTHORIZED,
  FORBIDDEN,
  TOO_MANY_REQUESTS,
  SERVER_ERROR,
  DATA_NOT_FOUND,
  UNKNOWN
}
