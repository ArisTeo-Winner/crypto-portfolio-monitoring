package com.mx.cryptomonitor.portfolio.application.port.out;

import java.util.List;
import java.util.UUID;

public interface TransactionHistoryPort {

  List<PortfolioTransactionSnapshot> getTransactionsByUser(UUID userId);
}
