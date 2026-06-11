package com.mx.cryptomonitor.transaction.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;

public interface DividendDetailRepository extends JpaRepository<DividendDetail, UUID> {
  Optional<DividendDetail> findByTransactionTransactionId(UUID transactionId);
}
