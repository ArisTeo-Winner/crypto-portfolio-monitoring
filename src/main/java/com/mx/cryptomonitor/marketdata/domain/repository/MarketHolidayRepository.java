package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.MarketHolidayEntity;

public interface MarketHolidayRepository
    extends JpaRepository<MarketHolidayEntity, java.util.UUID> {

  List<MarketHolidayEntity> findByMarket(String market);
}
