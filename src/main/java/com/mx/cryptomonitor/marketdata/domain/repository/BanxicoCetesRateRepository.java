package com.mx.cryptomonitor.marketdata.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.marketdata.domain.model.BanxicoCetesRateEntity;

@Repository
public interface BanxicoCetesRateRepository extends JpaRepository<BanxicoCetesRateEntity, UUID> {

  @Query("SELECT MAX(b.auctionDate) FROM BanxicoCetesRateEntity b")
  Optional<LocalDate> findLatestAuctionDate();

  List<BanxicoCetesRateEntity> findByAuctionDate(LocalDate auctionDate);

  @Modifying
  @Query(
      value =
          "INSERT INTO banxico_cetes_rate (id, term_days, rate, auction_date, fetched_at) "
              + "VALUES (:id, :termDays, :rate, :auctionDate, :fetchedAt) "
              + "ON CONFLICT (term_days, auction_date) "
              + "DO UPDATE SET rate = EXCLUDED.rate, fetched_at = EXCLUDED.fetched_at",
      nativeQuery = true)
  void upsert(
      @Param("id") UUID id,
      @Param("termDays") Integer termDays,
      @Param("rate") BigDecimal rate,
      @Param("auctionDate") LocalDate auctionDate,
      @Param("fetchedAt") OffsetDateTime fetchedAt);
}
