package com.mx.cryptomonitor.asset.infrastructure.outbound.fmp;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.infrastructure.outbound.companieslogo.CompaniesLogoAdapter;

@Component
public class FmpCatalogAdapter implements CatalogFetchPort {

  private static final Logger log = LoggerFactory.getLogger(FmpCatalogAdapter.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final WebClient webClient;
  private final String apiKey;
  private final CompaniesLogoAdapter companiesLogoAdapter;

  public FmpCatalogAdapter(
      WebClient.Builder builder,
      @Value("${fmp.base-url:https://financialmodelingprep.com}") String baseUrl,
      @Value("${fmp.api-key:}") String apiKey,
      CompaniesLogoAdapter companiesLogoAdapter) {
    this.webClient = builder.baseUrl(baseUrl).build();
    this.apiKey = apiKey;
    this.companiesLogoAdapter = companiesLogoAdapter;
  }

  public List<AssetCatalogDto> fetchTopStocks(int limit) {
    try {
      List<FmpStockScreenerItem> items =
          webClient
              .get()
              .uri(
                  u ->
                      u.path("/api/v3/stock-screener")
                          .queryParam("marketCapMoreThan", 100_000_000_000L)
                          .queryParam("exchange", "NASDAQ,NYSE")
                          .queryParam("limit", limit)
                          .queryParam("apikey", apiKey)
                          .build())
              .retrieve()
              .bodyToFlux(FmpStockScreenerItem.class)
              .collectList()
              .timeout(TIMEOUT)
              .block();

      if (items == null) return List.of();

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
    } catch (Exception e) {
      log.warn("FMP fetchTopStocks failed: {}", e.getMessage());
      return List.of();
    }
  }

  public List<AssetCatalogDto> fetchTopEtfs(int limit) {
    try {
      List<FmpEtfItem> items =
          webClient
              .get()
              .uri(u -> u.path("/api/v3/etf/list").queryParam("apikey", apiKey).build())
              .retrieve()
              .bodyToFlux(FmpEtfItem.class)
              .take(limit)
              .collectList()
              .timeout(TIMEOUT)
              .block();

      if (items == null) return List.of();

      return items.stream()
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
    } catch (Exception e) {
      log.warn("FMP fetchTopEtfs failed: {}", e.getMessage());
      return List.of();
    }
  }

  private record FmpStockScreenerItem(
      String symbol, String companyName, String exchangeShortName, Long marketCap) {}

  private record FmpEtfItem(String symbol, String name) {}
}
