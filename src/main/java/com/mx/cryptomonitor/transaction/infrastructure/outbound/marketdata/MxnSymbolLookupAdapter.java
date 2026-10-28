package com.mx.cryptomonitor.transaction.infrastructure.outbound.marketdata;

import java.util.List;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.MxnSymbolLookupPort;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MxnSymbolLookupAdapter implements MxnSymbolLookupPort {

  private final TransactionRepository transactionRepository;

  @Override
  public List<String> findDistinctMxnSymbols() {
    return transactionRepository.findDistinctMxnSymbols();
  }
}
