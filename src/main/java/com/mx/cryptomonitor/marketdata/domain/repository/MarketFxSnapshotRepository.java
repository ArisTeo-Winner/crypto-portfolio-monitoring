package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.marketdata.domain.model.MarketFxSnapshotEntity;

@Repository
public interface MarketFxSnapshotRepository extends JpaRepository<MarketFxSnapshotEntity, UUID> {

  Optional<MarketFxSnapshotEntity> findFirstByTickerOrderByQuoteAtDesc(String ticker);
}
