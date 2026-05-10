package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.UpdateTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionDetailsResponse;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionQueryUseCase;
import com.mx.cryptomonitor.transaction.application.port.out.PortfolioProjectionSyncPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionAuditPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionRegistrationPort;
import com.mx.cryptomonitor.transaction.domain.exception.InvalidTransactionException;
import com.mx.cryptomonitor.transaction.domain.exception.TransactionNotFoundException;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService implements TransactionCommandUseCase, TransactionQueryUseCase {

  private static final Sort RECENT_FIRST_SORT =
      Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt"));
  private static final String CREATE_TRANSACTION_SCOPE = "transactions:create";
  private static final String CREATE_BUY_SCOPE = "transactions:buy";
  private static final String CREATE_SELL_SCOPE = "transactions:sell";
  private static final String CREATE_TRANSFER_SCOPE = "transactions:transfer";

  private final TransactionRepository transactionRepository;
  private final TransactionRegistrationPort transactionRegistrationPort;
  private final PortfolioProjectionSyncPort portfolioProjectionSyncPort;
  private final TransactionMapper transactionMapper;
  private final TransactionIdempotencyService transactionIdempotencyService;
  private final TransactionAuditPort transactionAuditPort;
  private final TransactionRealizedPnlService transactionRealizedPnlService;

  @Override
  public TransactionResponse registerTransaction(
      UUID userId, TransactionRequest request, String idempotencyKey) {
    return registerWithIdempotency(
        userId, CREATE_TRANSACTION_SCOPE, request, idempotencyKey, "Create transaction");
  }

  @Override
  public TransactionResponse registerBuyTransaction(
      UUID userId, BuyTransactionRequest request, String idempotencyKey) {
    return registerWithIdempotency(
        userId,
        CREATE_BUY_SCOPE,
        toLegacyRequest(request),
        idempotencyKey,
        "Create buy transaction");
  }

  @Override
  public TransactionResponse registerSellTransaction(
      UUID userId, SellTransactionRequest request, String idempotencyKey) {
    return registerWithIdempotency(
        userId,
        CREATE_SELL_SCOPE,
        toLegacyRequest(request),
        idempotencyKey,
        "Create sell transaction");
  }

  @Override
  public TransactionResponse registerTransferTransaction(
      UUID userId, TransferTransactionRequest request, String idempotencyKey) {
    return registerWithIdempotency(
        userId,
        CREATE_TRANSFER_SCOPE,
        toLegacyRequest(request),
        idempotencyKey,
        "Create transfer transaction");
  }

  @Transactional
  @Override
  public TransactionResponse updateTransaction(
      UUID userId, UUID transactionId, UpdateTransactionRequest request, String idempotencyKey) {
    return transactionIdempotencyService.executeForTransactionResponse(
        userId,
        updateScope(transactionId),
        idempotencyKey,
        request,
        () -> {
          try {
            Transaction transaction =
                transactionRepository
                    .findByTransactionIdAndUserId(transactionId, userId)
                    .orElseThrow(
                        () -> new TransactionNotFoundException("Transaction no encontrada"));

            String transactionType = normalizeTransactionType(transaction.getTransactionType());
            validateUpdateRequest(transactionType, request);

            transaction.setAssetSymbol(request.assetSymbol().trim().toUpperCase(Locale.ROOT));
            transaction.setAssetType(normalizedAssetType(request.assetType()));
            transaction.setQuantity(request.quantity());
            transaction.setPricePerUnit(
                resolvePricePerUnit(transactionType, request.pricePerUnit()));
            transaction.setTotalValue(
                calculateTotalValue(
                    transactionType, request.quantity(), transaction.getPricePerUnit()));
            transaction.setTransactionDate(request.transactionDate());
            transaction.setFee(normalizedFee(request.fee()));
            transaction.setNotes(request.notes());
            transaction.setTransferType(
                resolveTransferType(transactionType, request.transferType()));
            transaction.setUpdatedAt(LocalDateTime.now());

            Transaction saved = transactionRepository.save(transaction);
            portfolioProjectionSyncPort.reconcileUserPortfolio(userId);
            transactionRealizedPnlService.rebuildUserRealizedPnl(userId);
            portfolioProjectionSyncPort.recordUserPortfolioSnapshot(userId);
            portfolioProjectionSyncPort
                .resolvePortfolioEntryId(userId, saved.getAssetSymbol())
                .ifPresent(saved::setPortfolioEntryId);

            TransactionResponse response =
                transactionMapper.toResponse(transactionRepository.save(saved));
            transactionAuditPort.logUpdateSuccess(
                userId, describeSuccessfulMutation("Updated transaction", response));
            return response;
          } catch (RuntimeException ex) {
            log.error(
                "Error updating transaction {} for user {}: {}",
                transactionId,
                userId,
                ex.getMessage(),
                ex);
            try {
              transactionAuditPort.logUpdateFailure(
                  userId, describeFailedUpdate(transactionId, request, ex));
            } catch (Exception auditEx) {
              log.error("Failed to log audit for transaction update failure", auditEx);
            }
            throw ex;
          }
        });
  }

  @Override
  public TransactionDetailsResponse getTransactionDetails(UUID userId, UUID transactionId) {
    Transaction transaction =
        transactionRepository
            .findByTransactionIdAndUserId(transactionId, userId)
            .orElseThrow(() -> new RuntimeException("Transaction not found"));

    BigDecimal fee = normalizedFee(transaction.getFee());
    BigDecimal grossAmount = transaction.getTotalValue();
    BigDecimal netAmount = null;
    String amountLabel = null;

    if ("BUY".equalsIgnoreCase(transaction.getTransactionType())) {
      netAmount = grossAmount.add(fee);
      amountLabel = "Total Spent";
    } else if ("SELL".equalsIgnoreCase(transaction.getTransactionType())) {
      netAmount = grossAmount.subtract(fee);
      amountLabel = "Total Received";
    }

    return new TransactionDetailsResponse(
        transaction.getTransactionId(),
        transaction.getAssetSymbol(),
        transaction.getAssetType(),
        transaction.getTransactionType(),
        transaction.getTransferType(),
        transaction.getTransactionDate(),
        transaction.getQuantity(),
        transaction.getPricePerUnit(),
        grossAmount,
        fee,
        inferFeeCurrency(transaction),
        netAmount,
        amountLabel,
        transaction.getNotes(),
        "MANUAL",
        null,
        "COMPLETED");
  }

  public List<TransactionResponse> getTransactionsByUser(UUID userId) {
    return transactionRepository.findByUserId(userId, RECENT_FIRST_SORT).stream()
        .map(transactionMapper::toResponse)
        .toList();
  }

  public List<TransactionResponse> getTransactionsByUserAndSymbol(UUID userId, String assetSymbol) {
    return transactionRepository
        .findByUserIdAndAssetSymbol(userId, assetSymbol, RECENT_FIRST_SORT)
        .stream()
        .map(transactionMapper::toResponse)
        .toList();
  }

  @Override
  public List<TransactionResponse> getTransactionsUser(
      UUID userId, String assetSymbol, String assetType, String transactionType) {

    if (assetSymbol != null && assetSymbol.isEmpty()) {
      assetSymbol = null;
    }
    if (assetType != null && assetType.isEmpty()) {
      assetType = null;
    }
    if (transactionType != null && transactionType.isEmpty()) {
      transactionType = null;
    }

    if (assetSymbol != null && assetType != null && transactionType != null) {
      AssetType normalizedAssetType = normalizedAssetType(assetType);
      return transactionRepository
          .findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(
              userId, assetSymbol, normalizedAssetType, transactionType)
          .stream()
          .map(transactionMapper::toResponse)
          .toList();
    }
    if (assetSymbol != null) {
      return transactionRepository
          .findByUserIdAndAssetSymbol(userId, assetSymbol, RECENT_FIRST_SORT)
          .stream()
          .map(transactionMapper::toResponse)
          .toList();
    }
    if (assetType != null) {
      return transactionRepository
          .findByUserIdAndAssetType(userId, normalizedAssetType(assetType), RECENT_FIRST_SORT)
          .stream()
          .map(transactionMapper::toResponse)
          .toList();
    }

    if (transactionType != null) {
      return transactionRepository
          .findByUserIdAndTransactionType(userId, transactionType, RECENT_FIRST_SORT)
          .stream()
          .map(transactionMapper::toResponse)
          .toList();
    }
    return transactionRepository.findByUserId(userId, RECENT_FIRST_SORT).stream()
        .map(transactionMapper::toResponse)
        .toList();
  }

  @Override
  public void deleteTransactionById(UUID userId, UUID idTransaction, String idempotencyKey) {
    transactionIdempotencyService.executeForVoid(
        userId,
        deleteScope(idTransaction),
        idempotencyKey,
        idTransaction,
        () -> {
          try {
            Transaction transaction =
                transactionRepository
                    .findByTransactionIdAndUserId(idTransaction, userId)
                    .orElseThrow(
                        () -> new TransactionNotFoundException("Transaction no encontrada"));

            transactionRepository.deleteById(transaction.getTransactionId());
            portfolioProjectionSyncPort.reconcileUserPortfolio(userId);
            transactionRealizedPnlService.rebuildUserRealizedPnl(userId);
            portfolioProjectionSyncPort.recordUserPortfolioSnapshot(userId);
            transactionAuditPort.logDeleteSuccess(userId, describeDeletedTransaction(transaction));
          } catch (RuntimeException ex) {
            log.error(
                "Error deleting transaction {} for user {}: {}",
                idTransaction,
                userId,
                ex.getMessage(),
                ex);
            try {
              transactionAuditPort.logDeleteFailure(
                  userId, describeFailedDelete(idTransaction, ex));
            } catch (Exception auditEx) {
              log.error("Failed to log audit for transaction delete failure", auditEx);
            }
            throw ex;
          }
        });
  }

  private TransactionResponse registerWithIdempotency(
      UUID userId,
      String operationScope,
      TransactionRequest request,
      String idempotencyKey,
      String actionLabel) {
    return transactionIdempotencyService.executeForTransactionResponse(
        userId,
        operationScope,
        idempotencyKey,
        request,
        () -> {
          try {
            TransactionResponse response =
                transactionRegistrationPort.registerTransaction(userId, request);
            transactionRealizedPnlService.rebuildUserRealizedPnl(userId);
            portfolioProjectionSyncPort.recordUserPortfolioSnapshot(userId);
            transactionAuditPort.logCreateSuccess(
                userId, describeSuccessfulMutation(actionLabel, response));
            return response;
          } catch (RuntimeException ex) {
            log.error("Error creating transaction for user {}: {}", userId, ex.getMessage(), ex);
            try {
              transactionAuditPort.logCreateFailure(
                  userId, describeFailedCreate(actionLabel, request, ex));
            } catch (RuntimeException auditEx) {
            } catch (Exception auditEx) {
              log.error("Failed to log audit for transaction creation failure", auditEx);
            }
            throw ex;
          }
        });
  }

  private void validateUpdateRequest(String transactionType, UpdateTransactionRequest request) {
    if ("TRANSFER".equals(transactionType)) {
      if (request.transferType() == null || request.transferType().isBlank()) {
        throw new InvalidTransactionException(
            "El tipo de transferencia es obligatorio para una transaccion TRANSFER");
      }
      return;
    }

    if (request.pricePerUnit() == null || request.pricePerUnit().compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidTransactionException(
          "El precio por unidad debe ser mayor que cero para transacciones BUY o SELL");
    }
  }

  private TransactionRequest toLegacyRequest(BuyTransactionRequest request) {
    return new TransactionRequest(
        request.assetSymbol().toUpperCase(),
        normalizedAssetType(request.assetType()),
        "BUY",
        request.quantity(),
        request.pricePerUnit(),
        calculateGrossAmount(request.quantity(), request.pricePerUnit()),
        request.transactionDate(),
        normalizedFee(request.fee()),
        request.notes());
  }

  private TransactionRequest toLegacyRequest(SellTransactionRequest request) {
    return new TransactionRequest(
        request.assetSymbol().toUpperCase(),
        normalizedAssetType(request.assetType()),
        "SELL",
        request.quantity(),
        request.pricePerUnit(),
        calculateGrossAmount(request.quantity(), request.pricePerUnit()),
        request.transactionDate(),
        normalizedFee(request.fee()),
        request.notes());
  }

  private TransactionRequest toLegacyRequest(TransferTransactionRequest request) {
    return new TransactionRequest(
        request.assetSymbol().toUpperCase(),
        normalizedAssetType(request.assetType()),
        "TRANSFER",
        request.quantity(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        request.transactionDate(),
        normalizedFee(request.fee()),
        request.notes(),
        request.transferType());
  }

  private BigDecimal calculateGrossAmount(BigDecimal quantity, BigDecimal pricePerUnit) {
    return quantity.multiply(pricePerUnit);
  }

  private BigDecimal calculateTotalValue(
      String transactionType, BigDecimal quantity, BigDecimal pricePerUnit) {
    if ("TRANSFER".equals(transactionType)) {
      return BigDecimal.ZERO;
    }
    return calculateGrossAmount(quantity, pricePerUnit);
  }

  private BigDecimal normalizedFee(BigDecimal fee) {
    return fee != null ? fee : BigDecimal.ZERO;
  }

  private AssetType normalizedAssetType(String assetType) {
    return AssetType.valueOf(assetType.trim().toUpperCase());
  }

  private String normalizeTransactionType(String transactionType) {
    return transactionType.trim().toUpperCase(Locale.ROOT);
  }

  private BigDecimal resolvePricePerUnit(String transactionType, BigDecimal pricePerUnit) {
    if ("TRANSFER".equals(transactionType)) {
      return BigDecimal.ZERO;
    }
    return pricePerUnit;
  }

  private String resolveTransferType(String transactionType, String transferType) {
    if ("TRANSFER".equals(transactionType)) {
      return transferType.trim().toUpperCase(Locale.ROOT);
    }
    return null;
  }

  private String inferFeeCurrency(Transaction transaction) {
    if ("TRANSFER".equalsIgnoreCase(transaction.getTransactionType())) {
      return transaction.getAssetSymbol();
    }
    return "USD";
  }

  private String updateScope(UUID transactionId) {
    return "transactions:update:" + transactionId;
  }

  private String deleteScope(UUID transactionId) {
    return "transactions:delete:" + transactionId;
  }

  private String describeSuccessfulMutation(String actionLabel, TransactionResponse response) {
    return String.format(
        "%s transactionId=%s asset=%s assetType=%s transactionType=%s quantity=%s transactionDate=%s",
        actionLabel,
        response.transactionId(),
        response.assetSymbol(),
        response.assetType(),
        response.transactionType(),
        response.quantity(),
        response.transactionDate());
  }

  private String describeFailedCreate(
      String actionLabel, TransactionRequest request, RuntimeException ex) {
    return String.format(
        "%s failed asset=%s assetType=%s transactionType=%s quantity=%s transactionDate=%s reason=%s",
        actionLabel,
        request.assetSymbol(),
        request.assetType(),
        request.transactionType(),
        request.quantity(),
        request.transactionDate(),
        ex.getMessage());
  }

  private String describeFailedUpdate(
      UUID transactionId, UpdateTransactionRequest request, RuntimeException ex) {
    return String.format(
        "Update transaction failed transactionId=%s asset=%s assetType=%s quantity=%s transactionDate=%s reason=%s",
        transactionId,
        request.assetSymbol(),
        request.assetType(),
        request.quantity(),
        request.transactionDate(),
        ex.getMessage());
  }

  private String describeDeletedTransaction(Transaction transaction) {
    return String.format(
        "Deleted transaction transactionId=%s asset=%s assetType=%s transactionType=%s quantity=%s transactionDate=%s",
        transaction.getTransactionId(),
        transaction.getAssetSymbol(),
        transaction.getAssetType(),
        transaction.getTransactionType(),
        transaction.getQuantity(),
        transaction.getTransactionDate());
  }

  private String describeFailedDelete(UUID transactionId, RuntimeException ex) {
    return String.format(
        "Delete transaction failed transactionId=%s reason=%s", transactionId, ex.getMessage());
  }
}
