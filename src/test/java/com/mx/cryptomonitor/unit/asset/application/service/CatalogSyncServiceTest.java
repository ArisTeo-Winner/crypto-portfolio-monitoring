package com.mx.cryptomonitor.unit.asset.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.application.port.out.IpoCalendarPort;
import com.mx.cryptomonitor.asset.application.port.out.IpoCalendarPort.IpoEntry;
import com.mx.cryptomonitor.asset.application.port.out.LogoResolverPort;
import com.mx.cryptomonitor.asset.application.port.out.StockProfilePort;
import com.mx.cryptomonitor.asset.application.port.out.StockProfilePort.StockProfile;
import com.mx.cryptomonitor.asset.application.service.CatalogSyncService;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpException;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpInvalidKeyException;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpPlanRestrictionException;

@ExtendWith(MockitoExtension.class)
class CatalogSyncServiceTest {

  @Mock private CatalogFetchPort fmpAdapter;
  @Mock private CatalogStorePort redisService;
  @Mock private AssetCatalogRepository catalogRepository;
  @Mock private LogoResolverPort logoResolver;
  @Mock private StockProfilePort stockProfilePort;
  @Mock private IpoCalendarPort ipoCalendarPort;

  @InjectMocks private CatalogSyncService syncService;

  // ── syncStocks / syncEtfs (backward-compat, Redis only) ───────────────────

  @Test
  void syncStocksSavesEachEntryAndPopulatesRankings() {
    List<AssetCatalogDto> stocks =
        List.of(
            new AssetCatalogDto("AAPL", "Apple Inc.", "STOCK", null, "NASDAQ", "USD", 3_000_000L),
            new AssetCatalogDto("MSFT", "Microsoft", "STOCK", null, "NASDAQ", "USD", 2_800_000L));
    when(fmpAdapter.fetchTopStocks(2)).thenReturn(stocks);

    syncService.syncStocks(2);

    verify(redisService).saveEntry(stocks.get(0));
    verify(redisService).saveEntry(stocks.get(1));
    verify(redisService).addToRanking("catalog:search:stock", "AAPL", 3_000_000.0);
    verify(redisService).addToRanking("catalog:search:stock", "MSFT", 2_800_000.0);
    verify(redisService).addToRanking("catalog:top10:stock", "AAPL", 3_000_000.0);
    verify(redisService).addToRanking("catalog:top10:stock", "MSFT", 2_800_000.0);
  }

  @Test
  void syncStocksDoesNothingWhenFmpReturnsEmpty() {
    when(fmpAdapter.fetchTopStocks(anyInt())).thenReturn(List.of());

    syncService.syncStocks(50);

    verify(redisService, never()).saveEntry(any());
  }

  @Test
  void syncEtfsSavesEachEntryAndPopulatesRankings() {
    List<AssetCatalogDto> etfs =
        List.of(
            new AssetCatalogDto("SPY", "SPDR S&P 500", "ETF", null, "NYSE", "USD", null),
            new AssetCatalogDto("QQQ", "Invesco QQQ", "ETF", null, "NASDAQ", "USD", null));
    when(fmpAdapter.fetchTopEtfs(2)).thenReturn(etfs);

    syncService.syncEtfs(2);

    verify(redisService).saveEntry(etfs.get(0));
    verify(redisService).saveEntry(etfs.get(1));
    verify(redisService).addToRanking(eq("catalog:search:etf"), eq("SPY"), anyDouble());
    verify(redisService).addToRanking(eq("catalog:search:etf"), eq("QQQ"), anyDouble());
  }

  @Test
  void syncEtfsDoesNothingWhenFmpReturnsEmpty() {
    when(fmpAdapter.fetchTopEtfs(anyInt())).thenReturn(List.of());

    syncService.syncEtfs(50);

    verify(redisService, never()).saveEntry(any());
  }

  @Test
  void syncDailyRankingReordersByRealFinnhubMarketCap() {
    AssetCatalogEntity nvda = stockEntity("NVDA", 3_000_000L);
    when(catalogRepository.findByAssetType("STOCK")).thenReturn(List.of(nvda));
    when(stockProfilePort.getProfile("NVDA"))
        .thenReturn(
            Optional.of(new StockProfile("NVDA", "NVIDIA", null, 3_500_000L, "NASDAQ", "USD")));

    syncService.syncDailyRanking();

    verify(redisService).addToRanking("catalog:top10:stock", "NVDA", 3_500_000.0);
    verify(redisService).addToRanking("catalog:search:stock", "NVDA", 3_500_000.0);
    verify(catalogRepository).save(nvda);
  }

  @Test
  void syncDailyRankingKeepsPreviousMarketCapWhenFinnhubHasNoDataForSymbol() {
    AssetCatalogEntity aapl = stockEntity("AAPL", 3_000_000L);
    when(catalogRepository.findByAssetType("STOCK")).thenReturn(List.of(aapl));
    when(stockProfilePort.getProfile("AAPL")).thenReturn(Optional.empty());

    syncService.syncDailyRanking();

    verify(redisService).addToRanking("catalog:top10:stock", "AAPL", 3_000_000.0);
    verify(catalogRepository, never()).save(any());
  }

  @Test
  void syncDailyRankingKeepsPreviousMarketCapWhenFinnhubThrowsForOneSymbol() {
    AssetCatalogEntity aapl = stockEntity("AAPL", 3_000_000L);
    AssetCatalogEntity msft = stockEntity("MSFT", 2_800_000L);
    when(catalogRepository.findByAssetType("STOCK")).thenReturn(List.of(aapl, msft));
    when(stockProfilePort.getProfile("AAPL")).thenThrow(new RuntimeException("boom"));
    when(stockProfilePort.getProfile("MSFT"))
        .thenReturn(
            Optional.of(new StockProfile("MSFT", "Microsoft", null, 2_900_000L, "NASDAQ", "USD")));

    syncService.syncDailyRanking();

    // AAPL fallo: conserva su market cap previo y no aborta el resto del job.
    verify(redisService).addToRanking("catalog:top10:stock", "AAPL", 3_000_000.0);
    verify(redisService).addToRanking("catalog:top10:stock", "MSFT", 2_900_000.0);
  }

  @Test
  void syncDailyRankingDoesNothingWhenCatalogHasNoStocks() {
    when(catalogRepository.findByAssetType("STOCK")).thenReturn(List.of());

    syncService.syncDailyRanking();

    verify(redisService, never()).addToRanking(eq("catalog:top10:stock"), any(), anyDouble());
    verify(stockProfilePort, never()).getProfile(any());
  }

  private AssetCatalogEntity stockEntity(String symbol, Long marketCap) {
    return AssetCatalogEntity.builder()
        .symbol(symbol)
        .name(symbol)
        .assetType("STOCK")
        .exchange("NASDAQ")
        .currency("USD")
        .marketCap(marketCap)
        .popular(false)
        .updatedAt(OffsetDateTime.now())
        .build();
  }

  @Test
  void forceFullSyncDelegatestoSyncWeekly() {
    when(fmpAdapter.fetchTopStocks(anyInt())).thenReturn(List.of());
    when(fmpAdapter.fetchTopEtfs(anyInt())).thenReturn(List.of());
    when(catalogRepository.save(any(AssetCatalogEntity.class))).thenReturn(null);

    syncService.forceFullSync();

    verify(fmpAdapter).fetchTopStocks(50);
    verify(fmpAdapter).fetchTopEtfs(50);
    // Crypto and govt bond static lists are always saved to DB and Redis
    verify(catalogRepository, atLeastOnce()).save(any(AssetCatalogEntity.class));
    verify(redisService, atLeastOnce()).saveEntry(any(AssetCatalogDto.class));
  }

  // ── syncType ──────────────────────────────────────────────────────────────

  @Test
  void syncTypeStockUsesFinnhubForLogoAndMarketCap() {
    List<AssetCatalogDto> stocks =
        List.of(
            new AssetCatalogDto("AAPL", "Apple Inc.", "STOCK", null, "NASDAQ", "USD", 3_000_000L));
    when(fmpAdapter.fetchTopStocks(1)).thenReturn(stocks);
    when(catalogRepository.save(any(AssetCatalogEntity.class))).thenReturn(null);
    when(stockProfilePort.getProfile("AAPL"))
        .thenReturn(
            Optional.of(
                new StockProfile(
                    "AAPL",
                    "Apple Inc.",
                    "https://static2.finnhub.io/aapl.png",
                    3_100_000L,
                    "NASDAQ",
                    "USD")));

    syncService.syncType("stock", 1);

    AssetCatalogDto expected =
        new AssetCatalogDto(
            "AAPL",
            "Apple Inc.",
            "STOCK",
            "https://static2.finnhub.io/aapl.png",
            "NASDAQ",
            "USD",
            3_100_000L);
    verify(catalogRepository).save(any(AssetCatalogEntity.class));
    verify(redisService).saveEntry(expected);
    verify(redisService).addToRanking("catalog:search:stock", "AAPL", 3_100_000.0);
    verify(logoResolver, never()).buildLogoUrl(any());
  }

  @Test
  void syncTypeStockKeepsPreviousLogoAndMarketCapWhenFinnhubHasNoData() {
    List<AssetCatalogDto> stocks =
        List.of(
            new AssetCatalogDto("AAPL", "Apple Inc.", "STOCK", null, "NASDAQ", "USD", 3_000_000L));
    when(fmpAdapter.fetchTopStocks(1)).thenReturn(stocks);
    when(catalogRepository.save(any(AssetCatalogEntity.class))).thenReturn(null);
    when(stockProfilePort.getProfile("AAPL")).thenReturn(Optional.empty());

    syncService.syncType("stock", 1);

    AssetCatalogDto expected =
        new AssetCatalogDto("AAPL", "Apple Inc.", "STOCK", null, "NASDAQ", "USD", 3_000_000L);
    verify(redisService).saveEntry(expected);
  }

  @Test
  void syncTypeEtfStillUsesCompaniesLogoNotFinnhub() {
    List<AssetCatalogDto> etfs =
        List.of(
            new AssetCatalogDto("VOO", "Vanguard S&P 500 ETF", "ETF", null, "NYSE", "USD", null));
    when(fmpAdapter.fetchTopEtfs(1)).thenReturn(etfs);
    when(catalogRepository.save(any(AssetCatalogEntity.class))).thenReturn(null);
    when(logoResolver.buildLogoUrl("VOO"))
        .thenReturn("https://companieslogo.com/api/starter/stock-symbol/VOO");

    syncService.syncType("etf", 1);

    AssetCatalogDto expected =
        new AssetCatalogDto(
            "VOO",
            "Vanguard S&P 500 ETF",
            "ETF",
            "https://companieslogo.com/api/starter/stock-symbol/VOO",
            "NYSE",
            "USD",
            null);
    verify(redisService).saveEntry(expected);
    verify(stockProfilePort, never()).getProfile(any());
  }

  @Test
  void syncTypeAbortsAndPreservesCatalogOnInvalidKey() {
    when(fmpAdapter.fetchTopStocks(anyInt()))
        .thenThrow(new FmpInvalidKeyException("invalid API key"));

    syncService.syncType("stock", 50);

    verify(redisService, never()).saveEntry(any());
    verify(catalogRepository, never()).save(any());
  }

  @Test
  void syncTypeAbortsAndPreservesCatalogOnPlanRestriction() {
    when(fmpAdapter.fetchTopStocks(anyInt()))
        .thenThrow(new FmpPlanRestrictionException("endpoint restricted"));

    syncService.syncType("stock", 50);

    verify(redisService, never()).saveEntry(any());
    verify(catalogRepository, never()).save(any());
  }

  @Test
  void syncTypeAbortsAndPreservesCatalogOnGenericFmpException() {
    when(fmpAdapter.fetchTopStocks(anyInt())).thenThrow(new FmpException("Limit Reach"));

    syncService.syncType("stock", 50);

    verify(redisService, never()).saveEntry(any());
    verify(catalogRepository, never()).save(any());
  }

  @Test
  void syncTypeDoesNotOverwriteCatalogWhenFmpReturnsEmptyList() {
    when(fmpAdapter.fetchTopStocks(anyInt())).thenReturn(List.of());

    syncService.syncType("stock", 50);

    verify(redisService, never()).saveEntry(any());
    verify(catalogRepository, never()).save(any());
  }

  @Test
  void syncTypeGovernmentBondUsesStaticListWithoutFmpCall() {
    when(catalogRepository.save(any(AssetCatalogEntity.class))).thenReturn(null);

    syncService.syncType("government_bond", 50);

    verify(fmpAdapter, never()).fetchTopStocks(anyInt());
    verify(fmpAdapter, never()).fetchTopEtfs(anyInt());
    verify(catalogRepository, atLeastOnce()).save(any(AssetCatalogEntity.class));
    verify(redisService, atLeastOnce()).saveEntry(any(AssetCatalogDto.class));
  }

  @Test
  void syncTypeCryptoUsesStaticListWithoutFmpCall() {
    when(catalogRepository.save(any(AssetCatalogEntity.class))).thenReturn(null);

    syncService.syncType("crypto", 50);

    verify(fmpAdapter, never()).fetchTopStocks(anyInt());
    verify(fmpAdapter, never()).fetchTopEtfs(anyInt());
    verify(catalogRepository, atLeastOnce()).save(any(AssetCatalogEntity.class));
  }

  // ── discoverNewListings (IPO discovery) ─────────────────────────────────────

  @Test
  void discoverNewListingsAddsAPricedNasdaqIpoNotAlreadyInCatalog() {
    IpoEntry spcx =
        new IpoEntry(
            "SPCX", "SpaceCo Inc", "NASDAQ Global", LocalDate.now(), "priced", 5_000_000_000L);
    when(ipoCalendarPort.getRecentIpos(any(), any())).thenReturn(List.of(spcx));
    when(catalogRepository.existsById("SPCX")).thenReturn(false);
    when(stockProfilePort.getProfile("SPCX"))
        .thenReturn(
            Optional.of(
                new StockProfile(
                    "SPCX",
                    "SpaceCo Inc",
                    "https://static2.finnhub.io/spcx.png",
                    6_000_000L,
                    "NASDAQ",
                    "USD")));
    when(catalogRepository.save(any(AssetCatalogEntity.class))).thenReturn(null);

    syncService.discoverNewListings();

    AssetCatalogDto expected =
        new AssetCatalogDto(
            "SPCX",
            "SpaceCo Inc",
            "STOCK",
            "https://static2.finnhub.io/spcx.png",
            "NASDAQ",
            "USD",
            6_000_000L);
    verify(redisService).saveEntry(expected);
    verify(catalogRepository).save(any(AssetCatalogEntity.class));
  }

  @Test
  void discoverNewListingsIgnoresSymbolsAlreadyInCatalog() {
    IpoEntry spcx =
        new IpoEntry(
            "SPCX", "SpaceCo Inc", "NASDAQ Global", LocalDate.now(), "priced", 5_000_000_000L);
    when(ipoCalendarPort.getRecentIpos(any(), any())).thenReturn(List.of(spcx));
    when(catalogRepository.existsById("SPCX")).thenReturn(true);

    syncService.discoverNewListings();

    verify(redisService, never()).saveEntry(any());
    verify(catalogRepository, never()).save(any());
    verify(stockProfilePort, never()).getProfile(any());
  }

  @Test
  void discoverNewListingsIgnoresNonPricedIpos() {
    IpoEntry upcoming =
        new IpoEntry("FUTR", "Future Inc", "NASDAQ", LocalDate.now(), "expected", 5_000_000_000L);
    when(ipoCalendarPort.getRecentIpos(any(), any())).thenReturn(List.of(upcoming));

    syncService.discoverNewListings();

    verify(redisService, never()).saveEntry(any());
    verify(catalogRepository, never()).existsById(any());
  }

  @Test
  void discoverNewListingsIgnoresNonNasdaqNyseExchanges() {
    IpoEntry lse =
        new IpoEntry("LDN", "London Co", "LSE", LocalDate.now(), "priced", 5_000_000_000L);
    when(ipoCalendarPort.getRecentIpos(any(), any())).thenReturn(List.of(lse));

    syncService.discoverNewListings();

    verify(redisService, never()).saveEntry(any());
  }

  @Test
  void discoverNewListingsIgnoresMicroIposBelowThreshold() {
    IpoEntry tiny =
        new IpoEntry("TINY", "Tiny Co", "NASDAQ", LocalDate.now(), "priced", 1_000_000L);
    when(ipoCalendarPort.getRecentIpos(any(), any())).thenReturn(List.of(tiny));

    syncService.discoverNewListings();

    verify(redisService, never()).saveEntry(any());
  }

  @Test
  void discoverNewListingsDoesNotAbortOnIpoCalendarFailure() {
    when(ipoCalendarPort.getRecentIpos(any(), any())).thenThrow(new RuntimeException("boom"));

    syncService.discoverNewListings();

    verify(catalogRepository, never()).save(any());
  }
}
