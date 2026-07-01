package com.mx.cryptomonitor.unit.asset.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpInvalidKeyException;
import com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception.FmpPlanRestrictionException;

@ExtendWith(MockitoExtension.class)
class CatalogSyncServiceTest {

  @Mock private CatalogFetchPort fmpAdapter;
  @Mock private CatalogStorePort redisService;
  @Mock private AssetCatalogRepository catalogRepository;

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
  void syncTypeStockPersistsToBothDbAndRedis() {
    List<AssetCatalogDto> stocks =
        List.of(
            new AssetCatalogDto("AAPL", "Apple Inc.", "STOCK", null, "NASDAQ", "USD", 3_000_000L));
    when(fmpAdapter.fetchTopStocks(1)).thenReturn(stocks);
    when(catalogRepository.save(any(AssetCatalogEntity.class))).thenReturn(null);

    syncService.syncType("stock", 1);

    verify(catalogRepository).save(any(AssetCatalogEntity.class));
    verify(redisService).saveEntry(stocks.get(0));
    verify(redisService).addToRanking("catalog:search:stock", "AAPL", 3_000_000.0);
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
}
