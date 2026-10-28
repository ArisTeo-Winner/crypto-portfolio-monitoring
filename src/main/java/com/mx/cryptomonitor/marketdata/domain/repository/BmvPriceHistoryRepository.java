package com.mx.cryptomonitor.marketdata.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.marketdata.domain.model.BmvPriceHistoryEntity;

@Repository
public interface BmvPriceHistoryRepository extends JpaRepository<BmvPriceHistoryEntity, UUID> {

  List<BmvPriceHistoryEntity> findByEmisoraSerieAndTradeDateBetween(
      String emisoraSerie, LocalDate from, LocalDate to);

  @Modifying
  @Query(
      value =
          "INSERT INTO bmv_price_history "
              + "(id, emisora_serie, trade_date, close_price, amount_traded, provider, cached_at) "
              + "VALUES (:id, :emisoraSerie, :tradeDate, :closePrice, :amountTraded, :provider, :cachedAt) "
              + "ON CONFLICT (emisora_serie, trade_date) DO NOTHING",
      nativeQuery = true)
  void upsert(
      @Param("id") UUID id,
      @Param("emisoraSerie") String emisoraSerie,
      @Param("tradeDate") LocalDate tradeDate,
      @Param("closePrice") BigDecimal closePrice,
      @Param("amountTraded") BigDecimal amountTraded,
      @Param("provider") String provider,
      @Param("cachedAt") OffsetDateTime cachedAt);
}
