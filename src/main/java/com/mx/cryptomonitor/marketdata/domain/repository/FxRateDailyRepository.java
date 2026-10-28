package com.mx.cryptomonitor.marketdata.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.marketdata.domain.model.FxRateDailyEntity;

@Repository
public interface FxRateDailyRepository extends JpaRepository<FxRateDailyEntity, UUID> {

  Optional<FxRateDailyEntity> findFirstByTickerAndRateDateLessThanEqualOrderByRateDateDesc(
      String ticker, LocalDate rateDate);

  List<FxRateDailyEntity> findByTickerAndRateDateBetween(
      String ticker, LocalDate from, LocalDate to);
}
