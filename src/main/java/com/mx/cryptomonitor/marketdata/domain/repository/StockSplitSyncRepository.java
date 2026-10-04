package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.marketdata.domain.model.StockSplitSyncEntity;

@Repository
public interface StockSplitSyncRepository extends JpaRepository<StockSplitSyncEntity, String> {

  Optional<StockSplitSyncEntity> findByTicker(String ticker);
}
