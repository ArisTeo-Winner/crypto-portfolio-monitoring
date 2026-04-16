package com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;

import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.dto.CmcStatus;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public final class CmcErrorDecoder {
  private CmcErrorDecoder() {
    // utility
  }

  public static Mono<? extends Throwable> decode(ClientResponse response) {
    HttpStatusCode status = response.statusCode();
    int http = status.value();
    log.warn("CMC returned HTTP error status={}", http);
    return response
        .bodyToMono(CmcErrorWrapper.class)
        .map(wrapper -> buildExceptionFromWrapper(status, wrapper, null))
        .onErrorResume(
            ex ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> buildExceptionFromWrapper(status, null, body)));
  }

  private static CoinMarketCapClientException buildExceptionFromWrapper(
      HttpStatusCode status, CmcErrorWrapper wrapper, String rawBodyFallback) {
    int http = status.value();
    CmcStatus st = wrapper != null ? wrapper.status() : null;
    Integer providerCode = (st != null && st.error_code() != null) ? st.error_code() : http;
    String providerMsg =
        (st != null && st.error_message() != null)
            ? st.error_message()
            : ("CMC httpStatus=" + http);
    String rawBody = rawBodyFallback;
    CmcErrorType type = classifyError(status);
    String fullMsg =
        "CMC error httpStatus=%d error_code=%d msg=%s".formatted(http, providerCode, providerMsg);
    return new CoinMarketCapClientException(
        fullMsg, type, http, providerCode, providerMsg, rawBody);
  }

  private static CmcErrorType classifyError(HttpStatusCode status) {
    int http = status.value();
    if (status.is4xxClientError()) {
      return switch (http) {
        case 400 -> CmcErrorType.BAD_REQUEST;
        case 401 -> CmcErrorType.UNAUTHORIZED;
        case 403 -> CmcErrorType.FORBIDDEN;
        case 429 -> CmcErrorType.TOO_MANY_REQUESTS;
        default -> CmcErrorType.UNKNOWN;
      };
    }
    if (status.is5xxServerError()) {
      return CmcErrorType.SERVER_ERROR;
    }
    return CmcErrorType.UNKNOWN;
  }
}
