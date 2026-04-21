package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.cmc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;

import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CmcErrorDecoder;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CmcErrorType;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CoinMarketCapClientException;

class CmcErrorDecoderTest {

  @Test
  void decodeShouldMap429ToTooManyRequests() {
    ClientResponse response =
        ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
            .header("Content-Type", "application/json")
            .body(
                "{\"status\":{\"error_code\":1008,\"error_message\":\"API rate limit exceeded\"}}")
            .build();

    Throwable decoded = CmcErrorDecoder.decode(response).block();

    assertThat(decoded).isInstanceOf(CoinMarketCapClientException.class);
    CoinMarketCapClientException ex = (CoinMarketCapClientException) decoded;
    assertThat(ex.getType()).isEqualTo(CmcErrorType.TOO_MANY_REQUESTS);
    assertThat(ex.getHttpStatus()).isEqualTo(429);
    assertThat(ex.getProviderErrorCode()).isEqualTo(1008);
  }

  @Test
  void decodeShouldFallbackToRawBodyWhenWrapperCannotBeParsed() {
    ClientResponse response =
        ClientResponse.create(HttpStatus.BAD_REQUEST)
            .header("Content-Type", "text/plain")
            .body("bad request body")
            .build();

    Throwable decoded = CmcErrorDecoder.decode(response).block();

    assertThat(decoded).isInstanceOf(CoinMarketCapClientException.class);
    CoinMarketCapClientException ex = (CoinMarketCapClientException) decoded;
    assertThat(ex.getType()).isEqualTo(CmcErrorType.BAD_REQUEST);
    assertThat(ex.getHttpStatus()).isEqualTo(400);
    assertThat(ex.getRawBody()).isEqualTo("bad request body");
  }

  @Test
  void decodeShouldMap5xxToServerError() {
    ClientResponse response =
        ClientResponse.create(HttpStatus.BAD_GATEWAY)
            .header("Content-Type", "application/json")
            .body("{\"status\":{\"error_code\":5000,\"error_message\":\"gateway failure\"}}")
            .build();

    Throwable decoded = CmcErrorDecoder.decode(response).block();

    assertThat(decoded).isInstanceOf(CoinMarketCapClientException.class);
    CoinMarketCapClientException ex = (CoinMarketCapClientException) decoded;
    assertThat(ex.getType()).isEqualTo(CmcErrorType.SERVER_ERROR);
    assertThat(ex.getHttpStatus()).isEqualTo(502);
  }

  @Test
  void decodeShouldMap401ToUnauthorized() {
    ClientResponse response =
        ClientResponse.create(HttpStatus.UNAUTHORIZED)
            .header("Content-Type", "application/json")
            .body("{\"status\":{\"error_code\":1002,\"error_message\":\"invalid key\"}}")
            .build();

    Throwable decoded = CmcErrorDecoder.decode(response).block();

    assertThat(decoded).isInstanceOf(CoinMarketCapClientException.class);
    CoinMarketCapClientException ex = (CoinMarketCapClientException) decoded;
    assertThat(ex.getType()).isEqualTo(CmcErrorType.UNAUTHORIZED);
    assertThat(ex.getHttpStatus()).isEqualTo(401);
  }

  @Test
  void decodeShouldMap403ToForbidden() {
    ClientResponse response =
        ClientResponse.create(HttpStatus.FORBIDDEN)
            .header("Content-Type", "application/json")
            .body("{\"status\":{\"error_code\":1003,\"error_message\":\"forbidden\"}}")
            .build();

    Throwable decoded = CmcErrorDecoder.decode(response).block();

    assertThat(decoded).isInstanceOf(CoinMarketCapClientException.class);
    CoinMarketCapClientException ex = (CoinMarketCapClientException) decoded;
    assertThat(ex.getType()).isEqualTo(CmcErrorType.FORBIDDEN);
    assertThat(ex.getHttpStatus()).isEqualTo(403);
  }

  @Test
  void decodeShouldMapUnknown4xxToUnknown() {
    ClientResponse response =
        ClientResponse.create(HttpStatus.I_AM_A_TEAPOT)
            .header("Content-Type", "application/json")
            .body("{\"status\":{\"error_code\":1999,\"error_message\":\"teapot\"}}")
            .build();

    Throwable decoded = CmcErrorDecoder.decode(response).block();

    assertThat(decoded).isInstanceOf(CoinMarketCapClientException.class);
    CoinMarketCapClientException ex = (CoinMarketCapClientException) decoded;
    assertThat(ex.getType()).isEqualTo(CmcErrorType.UNKNOWN);
    assertThat(ex.getHttpStatus()).isEqualTo(418);
  }
}
