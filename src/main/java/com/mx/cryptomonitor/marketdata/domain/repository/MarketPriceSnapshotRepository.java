package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.marketdata.domain.model.MarketPriceSnapshotEntity;

@Repository
public interface MarketPriceSnapshotRepository
    extends JpaRepository<MarketPriceSnapshotEntity, UUID> {

  Optional<MarketPriceSnapshotEntity>
      findFirstByAssetSymbolAndExchangeAndCurrencyOrderByQuoteTimestampDesc(
          String assetSymbol, String exchange, String currency);

  List<MarketPriceSnapshotEntity> findByAssetSymbolAndExchangeAndCurrency(
      String assetSymbol, String exchange, String currency, Sort sort);
}
