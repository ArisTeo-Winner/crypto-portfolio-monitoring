package com.mx.cryptomonitor.unit.asset.application.service;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.application.service.CatalogSyncService;

@ExtendWith(MockitoExtension.class)
class CatalogSyncServiceTest {

  @Mock private CatalogFetchPort fmpAdapter;
  @Mock private CatalogStorePort redisService;
  @InjectMocks private CatalogSyncService syncService;

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
    // Both are within top 10
    verify(redisService).addToRanking("catalog:top10:stock", "AAPL", 3_000_000.0);
    verify(redisService).addToRanking("catalog:top10:stock", "MSFT", 2_800_000.0);
  }

  @Test
  void syncStocksDoesNothingWhenFmpReturnsEmpty() {
    when(fmpAdapter.fetchTopStocks(anyInt())).thenReturn(List.of());

    syncService.syncStocks(50);

    verify(redisService, never()).saveEntry(org.mockito.ArgumentMatchers.any());
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

    verify(redisService, never()).saveEntry(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void syncDailyRankingUpdatesTop10StockScores() {
    List<AssetCatalogDto> stocks =
        List.of(new AssetCatalogDto("NVDA", "NVIDIA", "STOCK", null, "NASDAQ", "USD", 3_500_000L));
    when(fmpAdapter.fetchTopStocks(10)).thenReturn(stocks);

    syncService.syncDailyRanking();

    verify(redisService).addToRanking("catalog:top10:stock", "NVDA", 3_500_000.0);
  }

  @Test
  void forceFullSyncDelegatestoSyncWeekly() {
    when(fmpAdapter.fetchTopStocks(anyInt())).thenReturn(List.of());
    when(fmpAdapter.fetchTopEtfs(anyInt())).thenReturn(List.of());

    syncService.forceFullSync();

    verify(fmpAdapter).fetchTopStocks(50);
    verify(fmpAdapter).fetchTopEtfs(50);
  }
}
