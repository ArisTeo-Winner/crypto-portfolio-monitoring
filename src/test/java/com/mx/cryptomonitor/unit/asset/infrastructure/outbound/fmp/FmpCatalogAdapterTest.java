package com.mx.cryptomonitor.unit.asset.infrastructure.outbound.fmp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.infrastructure.outbound.companieslogo.CompaniesLogoAdapter;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.FmpCatalogAdapter;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpException;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpInvalidKeyException;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpPlanRestrictionException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class FmpCatalogAdapterTest {

  private MockWebServer server;
  private FmpCatalogAdapter adapter;
  private CompaniesLogoAdapter companiesLogoAdapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    companiesLogoAdapter = mock(CompaniesLogoAdapter.class);
    when(companiesLogoAdapter.buildLogoUrl("SPY"))
        .thenReturn("https://companieslogo.com/api/starter/stock-symbol/SPY");
    adapter =
        new FmpCatalogAdapter(
            WebClient.builder(),
            server.url("/").toString(),
            "test-key",
            companiesLogoAdapter,
            "/api/v3/stock-screener",
            "/api/v3/etf/list");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void fetchTopStocksReturnsMappedDtos() throws InterruptedException {
    server.enqueue(
        jsonResponse(
            """
            [
              {"symbol":"AAPL","companyName":"Apple Inc.","exchangeShortName":"NASDAQ","marketCap":3000000000000},
              {"symbol":"MSFT","companyName":"Microsoft Corporation","exchangeShortName":"NASDAQ","marketCap":2800000000000}
            ]
            """));

    List<AssetCatalogDto> result = adapter.fetchTopStocks(2);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).symbol()).isEqualTo("AAPL");
    assertThat(result.get(0).name()).isEqualTo("Apple Inc.");
    assertThat(result.get(0).assetType()).isEqualTo("STOCK");
    assertThat(result.get(0).exchange()).isEqualTo("NASDAQ");
    assertThat(result.get(0).currency()).isEqualTo("USD");
    assertThat(result.get(0).marketCap()).isEqualTo(3_000_000L);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/api/v3/stock-screener");
    assertThat(request.getRequestUrl().queryParameter("apikey")).isEqualTo("test-key");
    assertThat(request.getRequestUrl().queryParameter("limit")).isEqualTo("2");
  }

  @Test
  void fetchTopStocksReturnsEmptyListOnServerError() {
    server.enqueue(new MockResponse().setResponseCode(500));

    List<AssetCatalogDto> result = adapter.fetchTopStocks(10);

    assertThat(result).isEmpty();
  }

  @Test
  void fetchTopEtfsReturnsMappedDtos() throws InterruptedException {
    server.enqueue(
        jsonResponse(
            """
            [
              {"symbol":"SPY","name":"SPDR S&P 500 ETF Trust"},
              {"symbol":"QQQ","name":"Invesco QQQ Trust"}
            ]
            """));

    List<AssetCatalogDto> result = adapter.fetchTopEtfs(2);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).symbol()).isEqualTo("SPY");
    assertThat(result.get(0).name()).isEqualTo("SPDR S&P 500 ETF Trust");
    assertThat(result.get(0).assetType()).isEqualTo("ETF");
    assertThat(result.get(0).currency()).isEqualTo("USD");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/api/v3/etf/list");
    assertThat(request.getRequestUrl().queryParameter("apikey")).isEqualTo("test-key");
  }

  @Test
  void fetchTopEtfsReturnsEmptyListOnServerError() {
    server.enqueue(new MockResponse().setResponseCode(401));

    List<AssetCatalogDto> result = adapter.fetchTopEtfs(10);

    assertThat(result).isEmpty();
  }

  @Test
  void fetchTopStocksReturnsEmptyListOnEmptyApiKey() throws Exception {
    server.shutdown();
    server = new MockWebServer();
    server.start();
    adapter =
        new FmpCatalogAdapter(
            WebClient.builder(),
            server.url("/").toString(),
            "",
            companiesLogoAdapter,
            "/api/v3/stock-screener",
            "/api/v3/etf/list");

    server.enqueue(jsonResponse("[]"));

    List<AssetCatalogDto> result = adapter.fetchTopStocks(10);

    assertThat(result).isEmpty();
    assertThat(server.getRequestCount()).isOne();
  }

  @Test
  void fetchTopStocksThrowsFmpInvalidKeyExceptionOnInvalidKey() {
    server.enqueue(
        jsonResponse(
            """
            {"Error Message": "Invalid API KEY. Feel free to create a Free API Key or visit https://site.financialmodelingprep.com/faqs?search=why-is-my-api-key-invalid for more information."}
            """));

    assertThatThrownBy(() -> adapter.fetchTopStocks(10))
        .isInstanceOf(FmpInvalidKeyException.class)
        .hasMessageContaining("invalid API key");

    assertThat(server.getRequestCount()).isOne();
  }

  @Test
  void fetchTopEtfsThrowsFmpInvalidKeyExceptionOnInvalidKey() {
    server.enqueue(
        jsonResponse(
            """
            {"Error Message": "Invalid API KEY. Feel free to create a Free API Key or visit https://site.financialmodelingprep.com/faqs?search=why-is-my-api-key-invalid for more information."}
            """));

    assertThatThrownBy(() -> adapter.fetchTopEtfs(10))
        .isInstanceOf(FmpInvalidKeyException.class)
        .hasMessageContaining("invalid API key");

    assertThat(server.getRequestCount()).isOne();
  }

  @Test
  void fetchTopStocksThrowsFmpPlanRestrictionExceptionWhenEndpointRestricted() {
    server.enqueue(
        jsonResponse(
            """
            {"message": "Restricted Endpoint. Please upgrade your plan at https://financialmodelingprep.com/developer/docs/pricing"}
            """));

    assertThatThrownBy(() -> adapter.fetchTopStocks(10))
        .isInstanceOf(FmpPlanRestrictionException.class)
        .hasMessageContaining("restricted by current FMP plan");
  }

  @Test
  void fetchTopStocksPropagatesFmpExceptionOnGenericErrorMessage() {
    server.enqueue(
        jsonResponse(
            """
            {"Error Message": "Limit Reach. Please upgrade your plan or visit our documentation for more details."}
            """));

    assertThatThrownBy(() -> adapter.fetchTopStocks(50))
        .isInstanceOf(FmpException.class)
        .hasMessageContaining("Limit Reach");
  }

  @Test
  void fetchTopEtfsPropagatesFmpExceptionOnGenericErrorMessage() {
    server.enqueue(
        jsonResponse(
            """
            {"Error Message": "Limit Reach. Please upgrade your plan or visit our documentation for more details."}
            """));

    assertThatThrownBy(() -> adapter.fetchTopEtfs(50))
        .isInstanceOf(FmpException.class)
        .hasMessageContaining("Limit Reach");
  }

  @Test
  void fetchTopStocksReturnsEmptyListOnConnectionFailure() throws Exception {
    MockWebServer unreachableServer = new MockWebServer();
    unreachableServer.start();
    String unreachableUrl = unreachableServer.url("/").toString();
    unreachableServer.shutdown();

    adapter =
        new FmpCatalogAdapter(
            WebClient.builder(),
            unreachableUrl,
            "test-key",
            companiesLogoAdapter,
            "/api/v3/stock-screener",
            "/api/v3/etf/list");

    List<AssetCatalogDto> result = adapter.fetchTopStocks(10);

    assertThat(result).isEmpty();
  }

  @Test
  void fetchTopStocksReturnsEmptyListOnServiceUnavailable() {
    server.enqueue(new MockResponse().setResponseCode(503));

    List<AssetCatalogDto> result = adapter.fetchTopStocks(10);

    assertThat(result).isEmpty();
  }

  private MockResponse jsonResponse(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
