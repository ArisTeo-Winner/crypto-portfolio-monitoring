package com.mx.cryptomonitor.transaction.infrastructure.outbound.marketdata;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.GovBondPositionLookupPort;
import com.mx.cryptomonitor.marketdata.application.port.out.GovBondPositionView;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GovBondPositionLookupAdapter implements GovBondPositionLookupPort {

  private final TransactionRepository transactionRepository;

  @Override
  public Optional<GovBondPositionView> findByTransactionIdAndUserId(
      UUID transactionId, UUID userId) {
    return transactionRepository
        .findByTransactionIdAndUserId(transactionId, userId)
        .map(this::toView);
  }

  private GovBondPositionView toView(Transaction transaction) {
    return new GovBondPositionView(
        transaction.getTransactionId(),
        transaction.getAssetSymbol(),
        transaction.getAssetType().name(),
        transaction.getCurrency(),
        transaction.getQuantity(),
        transaction.getTotalValue(),
        transaction.getCouponRate(),
        transaction.getMaturityDate(),
        transaction.getFaceValue());
  }
}
