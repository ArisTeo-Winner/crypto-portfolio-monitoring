package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.marketdata.domain.model.StockSplitEntity;

@Repository
public interface StockSplitRepository extends JpaRepository<StockSplitEntity, UUID> {

  List<StockSplitEntity> findByTickerOrderByExecutionDateDesc(String ticker);
}
