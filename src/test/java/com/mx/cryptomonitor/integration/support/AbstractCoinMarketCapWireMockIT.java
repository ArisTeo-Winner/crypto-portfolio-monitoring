package com.mx.cryptomonitor.integration.support;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractCoinMarketCapWireMockIT {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected static WireMockServer wireMockServer;

  @BeforeAll
  void starWireMock() {
    if (wireMockServer == null) {
      wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    }
    log.info(">>> Iniciando WireMock en puerto dinámico…");
    wireMockServer.start();
    configureFor("localhost", wireMockServer.port());

    log.info(">>> WireMock iniciado en http://localhost:{}", wireMockServer.port());
  }

  @AfterAll
  void stopWireMock() {
    if (wireMockServer != null && wireMockServer.isRunning()) {
      log.info(">>> Deteniendo WireMock…");
      wireMockServer.stop();
    }
  }

  @BeforeEach
  void resetWireMock() {
    wireMockServer.resetAll();
  }

  @DynamicPropertySource
  static void overrideExternalApiProperties(DynamicPropertyRegistry registry) {
    /**/
    if (wireMockServer == null) {
      wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
      wireMockServer.start();
      System.out.println(
          "DEBUG: WireMock started in DynamicPropertySource on port: " + wireMockServer.port());
    }

    String baseUrl = "http://localhost:" + wireMockServer.port();
    System.out.println("DEBUG: Setting external.providers.coinmarketcap.base-url to: " + baseUrl);

    registry.add("external.providers.coinmarketcap.base-url", () -> baseUrl);
    registry.add("external.providers.coinmarketcap.api-key", () -> "fake-api-key");
    registry.add("external.providers.coinmarketcap.response-timeout", () -> "3s");
    registry.add("external.providers.coinmarketcap.cache-ttl", () -> "15s");
    registry.add("external.providers.coinmarketcap.default-convert", () -> "USD");
    registry.add("external.providers.coinmarketcap.enabled", () -> "true");
  }

  /*
  protected void stubCoinMarketCapQuote(String symbol, String price) {
  	String body = """
  {
  	"status": {
  			"timestamp": "2025-12-12T19:29:27.882Z",
  			"error_code": 0,
  			"error_message": null,
  			"elapsed": 25,
  			"credit_count": 1,
  			"notice": null
  	},
  	"data": {
  		"%s": [
  			{

  				"id": 1,
  				"name": "%s",
  				"symbol": "%s",
  				"slug": "bitcoin",
  				"num_market_pairs": 1000,
  				"date_added": "2013-04-28T00:00:00.000Z",
  				"tags": [
  					"mineable"
  				],
  				"max_supply": 21000000,
  				"circulating_supply": 19000000,
  				"total_supply": 19000000,
  				"platform": null,
  				"cmc_rank": 1,
  				"last_updated": "2023-10-27T10:00:00.000Z",
  				"quote": {
                     	"USD": {
                         	"price": "%s",
                         	"volume_24h": 82343732639.0106,
                         	"volume_change_24h": 24.4108,
                         	"percent_change_1h": -0.25291902,
                         	"percent_change_24h": -0.9011698,
                         	"percent_change_7d": 0.82584513,
                         	"percent_change_30d": -10.82497456,
                         	"percent_change_60d": -22.01572062,
                         	"percent_change_90d": -22.01430867,
                         	"market_cap": 1800800991687.021,
                         	"market_cap_dominance": 58.8463,
                         	"fully_diluted_market_cap": 1894510233967.01,
                         	"tvl": null,
                         	"last_updated": "2025-12-12T19:27:00.000Z"
  					}
                 	 }
  			  }
  			]
  		}
  	}
  		""".formatted(symbol,symbol, symbol,price);

  		    stubFor(
  			get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
  			.withQueryParam("symbol", equalTo(symbol))
  			.withHeader("X-CMC_PRO_API_KEY", equalTo("fake-api-key"))
  			.willReturn(aResponse()
  					.withStatus(200)
  					.withHeader("Content-Type", "application/json")
  				//	.withBody(body)
  				));

  }
  */

  protected void stubCoinMarketCapError(
      String symbol, int httpStatus, int errorCode, String errorMessage) {

    String body =
        """
			{
				"status": {
						"timestamp": "2025-12-12T19:29:27.882Z",
						"error_code": %d,
						"error_message": "%s",
						"elapsed": 25,
						"credit_count": 1,
						"notice": null
					},
					"data": null
				}
			}
			"""
            .formatted(errorCode, errorMessage);

    log.info(">>> Stubbing CoinMarketCap error: {}", body);

    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo(symbol))
            // .withHeader("X-CMC_PRO_API_KEY", equalTo("fake-api-key"))
            .willReturn(
                aResponse()
                    .withStatus(httpStatus)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  protected void verifyCoinMarketCapQuoCalled(String symbol, int times) {
    wireMockServer.verify(
        times,
        getRequestedFor(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo(symbol))
            .withHeader("X-CMC_PRO_API_KEY", equalTo("fake-api-key")));
  }
}
