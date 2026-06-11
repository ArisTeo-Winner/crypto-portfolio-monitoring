package com.mx.cryptomonitor.transaction.infrastructure.outbound.portfolio;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioEntryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionRegistrationPort;
import com.mx.cryptomonitor.transaction.domain.exception.InvalidTransactionException;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.exception.UserNotFoundException;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioTransactionRegistrationAdapter implements TransactionRegistrationPort {

  private final UserRepository userRepository;
  private final PortfolioEntryPort portfolioEntryPort;
  private final TransactionRepository transactionRepository;
  private final TransactionMapper transactionMapper;

  @Override
  @Transactional
  public TransactionResponse registerTransaction(UUID userId, TransactionRequest request) {

    validateTransactionRequest(request);

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

    UUID portfolioEntryId =
        portfolioEntryPort.applyTransaction(userId, toPortfolioTransactionCommand(request));

    Transaction transaction = createTransaction(request, user, portfolioEntryId);
    transactionRepository.save(transaction);

    return transactionMapper.toResponse(transaction);
  }

  private void validateTransactionRequest(TransactionRequest request) {
    boolean isDividend = "DIVIDEND".equalsIgnoreCase(request.transactionType());
    if (!isDividend
        && request.assetType() != null
        && com.mx.cryptomonitor.transaction.domain.model.AssetType.INDEX == request.assetType()) {
      throw new InvalidTransactionException(
          "El tipo de activo INDEX no puede ser transaccionado directamente");
    }
    if (!isDividend && request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidTransactionException("La cantidad debe ser mayor que cero");
    }
    if (!"BUY".equalsIgnoreCase(request.transactionType())
        && !"SELL".equalsIgnoreCase(request.transactionType())
        && !"TRANSFER".equalsIgnoreCase(request.transactionType())
        && !isDividend) {
      throw new InvalidTransactionException(
          "Tipo de transaccion invalido: " + request.transactionType());
    }
    if ("TRANSFER".equalsIgnoreCase(request.transactionType())
        && request.transferType() != null
        && !"TRANSFER_IN".equalsIgnoreCase(request.transferType())
        && !"TRANSFER_OUT".equalsIgnoreCase(request.transferType())) {
      throw new InvalidTransactionException(
          "Tipo de transferencia invalido: " + request.transferType());
    }
  }

  private PortfolioTransactionCommand toPortfolioTransactionCommand(TransactionRequest request) {

    return new PortfolioTransactionCommand(
        request.assetSymbol(),
        request.assetType().toString(),
        request.transactionType(),
        request.transferType(),
        request.quantity(),
        request.totalValue(),
        request.pricePerUnit());
  }

  private Transaction createTransaction(
      TransactionRequest request, User user, UUID portfolioEntryId) {
    Transaction transaction = transactionMapper.toEntity(request);
    transaction.setUser(user);
    transaction.setPortfolioEntryId(portfolioEntryId);
    transaction.setTransactionDate(request.transactionDate());
    transaction.setTotalValue(request.totalValue());
    transaction.setTransferType(request.transferType());
    return transaction;
  }
}
