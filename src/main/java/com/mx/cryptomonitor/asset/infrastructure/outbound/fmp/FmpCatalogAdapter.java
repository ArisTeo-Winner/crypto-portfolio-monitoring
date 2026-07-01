package com.mx.cryptomonitor.asset.infrastructure.outbound.fmp;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.infrastructure.outbound.companieslogo.CompaniesLogoAdapter;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpException;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpInvalidKeyException;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpPlanRestrictionException;

@Component
public class FmpCatalogAdapter implements CatalogFetchPort {

  private static final Logger log = LoggerFactory.getLogger(FmpCatalogAdapter.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WebClient webClient;
  private final String apiKey;
  private final String screenerPath;
  private final String etfListPath;
  private final CompaniesLogoAdapter companiesLogoAdapter;

  public FmpCatalogAdapter(
      WebClient.Builder builder,
      @Value("${fmp.base-url:https://financialmodelingprep.com}") String baseUrl,
      @Value("${fmp.api-key:}") String apiKey,
      CompaniesLogoAdapter companiesLogoAdapter,
      @Value("${fmp.screener-path:/api/v3/stock-screener}") String screenerPath,
      @Value("${fmp.etf-list-path:/api/v3/etf/list}") String etfListPath) {
    this.webClient = builder.baseUrl(baseUrl).build();
    this.apiKey = apiKey;
    this.companiesLogoAdapter = companiesLogoAdapter;
    this.screenerPath = screenerPath;
    this.etfListPath = etfListPath;
  }

  @Override
  public List<AssetCatalogDto> fetchTopStocks(int limit) {
    try {
      String body =
          webClient
              .get()
              .uri(
                  u ->
                      u.path(screenerPath)
                          .queryParam("marketCapMoreThan", 100_000_000_000L)
                          .queryParam("exchange", "NASDAQ,NYSE")
                          .queryParam("limit", limit)
                          .queryParam("apikey", apiKey)
                          .build())
              .retrieve()
              .bodyToMono(String.class)
              .timeout(TIMEOUT)
              .block();

      if (body == null) return List.of();
      inspectForErrors("fetchTopStocks", body);

      List<FmpStockScreenerItem> items =
          MAPPER.readValue(body, new TypeReference<List<FmpStockScreenerItem>>() {});
      return items.stream()
          .map(
              i ->
                  new AssetCatalogDto(
                      i.symbol(),
                      i.companyName(),
                      "STOCK",
                      null,
                      i.exchangeShortName(),
                      "USD",
                      i.marketCap() != null ? i.marketCap() / 1_000_000 : null))
          .toList();
    } catch (FmpPlanRestrictionException | FmpInvalidKeyException e) {
      log.error("FMP permanent error in fetchTopStocks: {}", e.getMessage());
      throw e;
    } catch (Exception e) {
      log.warn("FMP fetchTopStocks failed: {}", e.getMessage());
      return List.of();
    }
  }

  @Override
  public List<AssetCatalogDto> fetchTopEtfs(int limit) {
    try {
      String body =
          webClient
              .get()
              .uri(u -> u.path(etfListPath).queryParam("apikey", apiKey).build())
              .retrieve()
              .bodyToMono(String.class)
              .timeout(TIMEOUT)
              .block();

      if (body == null) return List.of();
      inspectForErrors("fetchTopEtfs", body);

      List<FmpEtfItem> items = MAPPER.readValue(body, new TypeReference<List<FmpEtfItem>>() {});
      return items.stream()
          .limit(limit)
          .map(
              i ->
                  new AssetCatalogDto(
                      i.symbol(),
                      i.name(),
                      "ETF",
                      companiesLogoAdapter.buildLogoUrl(i.symbol()),
                      "NASDAQ",
                      "USD",
                      null))
          .toList();
    } catch (FmpPlanRestrictionException | FmpInvalidKeyException e) {
      log.error("FMP permanent error in fetchTopEtfs: {}", e.getMessage());
      throw e;
    } catch (Exception e) {
      log.warn("FMP fetchTopEtfs failed: {}", e.getMessage());
      return List.of();
    }
  }

  // Inspect raw body before JSON parsing to detect false-200 responses from FMP.
  private void inspectForErrors(String op, String body) {
    if (body.contains("Restricted Endpoint")) {
      throw new FmpPlanRestrictionException(op + ": endpoint restricted by current FMP plan");
    }
    if (body.contains("Invalid API KEY")) {
      throw new FmpInvalidKeyException(op + ": invalid API key");
    }
    if (body.contains("\"Error Message\"")) {
      throw new FmpException(op + ": " + extractErrorMessage(body));
    }
  }

  private String extractErrorMessage(String body) {
    try {
      return MAPPER.readTree(body).path("Error Message").asText("unknown FMP error");
    } catch (Exception e) {
      return "unknown FMP error";
    }
  }

  private record FmpStockScreenerItem(
      String symbol, String companyName, String exchangeShortName, Long marketCap) {}

  private record FmpEtfItem(String symbol, String name) {}
}
