package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogRefreshPort;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.DividendTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.ImportedStockTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionOrigin;
import com.mx.cryptomonitor.transaction.application.dto.request.TransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.UpdateTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.FrictionBreakdownView;
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
import com.mx.cryptomonitor.transaction.domain.friction.FrictionBreakdown;
import com.mx.cryptomonitor.transaction.domain.friction.FrictionSide;
import com.mx.cryptomonitor.transaction.domain.friction.GbmFrictionCalculator;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;
import com.mx.cryptomonitor.transaction.domain.model.DividendType;
import com.mx.cryptomonitor.transaction.domain.model.ImportSource;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
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
  private final DividendDetailRepository dividendDetailRepository;
  private final AssetCatalogQueryPort assetCatalogQueryPort;
  private final GbmFrictionCalculator frictionCalculator;
  private final AssetCatalogRefreshPort assetCatalogRefreshPort;

  @Value("${catalog.icon.pull-once.enabled:true}")
  private boolean iconPullOnceEnabled;

  @Override
  public TransactionResponse registerTransaction(
      UUID userId, TransactionRequest request, String idempotencyKey) {
    return registerWithIdempotency(
        userId, CREATE_TRANSACTION_SCOPE, request, idempotencyKey, "Create transaction");
  }

  @Override
  public TransactionResponse registerBuyTransaction(
      UUID userId, BuyTransactionRequest request, String idempotencyKey) {
    TransactionResponse response =
        registerWithIdempotency(
            userId,
            CREATE_BUY_SCOPE,
            toLegacyRequest(request),
            idempotencyKey,
            "Create buy transaction");
    applyManualFriction(
        userId,
        response,
        manualBreakdown(
            FrictionSide.BUY,
            request.quantity(),
            request.pricePerUnit(),
            request.brokerCommission(),
            request.brokerIva(),
            request.otherFees()));
    return response;
  }

  @Override
  public TransactionResponse registerSellTransaction(
      UUID userId, SellTransactionRequest request, String idempotencyKey) {
    TransactionResponse response =
        registerWithIdempotency(
            userId,
            CREATE_SELL_SCOPE,
            toLegacyRequest(request),
            idempotencyKey,
            "Create sell transaction");
    applyManualFriction(
        userId,
        response,
        manualBreakdown(
            FrictionSide.SELL,
            request.quantity(),
            request.pricePerUnit(),
            request.brokerCommission(),
            request.brokerIva(),
            request.otherFees()));
    return response;
  }

  private FrictionBreakdown manualBreakdown(
      FrictionSide side,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal commission,
      BigDecimal iva,
      BigDecimal otherFees) {
    if (commission == null && iva == null && otherFees == null) {
      return null;
    }
    return frictionCalculator.manual(side, quantity, unitPrice, commission, iva, otherFees, null);
  }

  private void applyManualFriction(
      UUID userId, TransactionResponse response, FrictionBreakdown breakdown) {
    if (breakdown == null) {
      return;
    }
    transactionRepository
        .findByTransactionIdAndUserId(response.transactionId(), userId)
        .ifPresent(
            transaction -> {
              transaction.applyFriction(breakdown);
              transactionRepository.save(transaction);
            });
  }

  private BigDecimal resolveManualFee(
      BigDecimal fee, BigDecimal commission, BigDecimal iva, BigDecimal otherFees) {
    if (commission == null && iva == null && otherFees == null) {
      return normalizedFee(fee);
    }
    return normalizedFee(commission).add(normalizedFee(iva)).add(normalizedFee(otherFees));
  }

  @Override
  public TransactionResponse registerImportedStockTransaction(
      UUID userId, ImportedStockTransactionRequest request, String idempotencyKey) {
    FrictionSide side = request.buy() ? FrictionSide.BUY : FrictionSide.SELL;
    FrictionBreakdown breakdown =
        switch (request.brokerKind()) {
          case GBM_MX_EQUITY -> frictionCalculator.mexicanEquity(
              side, request.quantity(), request.pricePerUnit(), request.netAmount());
          case DRIVEWEALTH -> frictionCalculator.driveWealth(
              side,
              request.quantity(),
              request.principalAmount(),
              request.commission(),
              request.transactionFee(),
              request.otherFees(),
              request.netAmount());
        };

    String assetName =
        (request.assetName() == null || request.assetName().isBlank())
            ? assetCatalogQueryPort.findNameBySymbol(request.assetSymbol()).orElse(null)
            : request.assetName();

    TransactionRequest legacy =
        new TransactionRequest(
            request.assetSymbol().toUpperCase(Locale.ROOT),
            AssetType.STOCK,
            request.buy() ? "BUY" : "SELL",
            request.quantity(),
            request.pricePerUnit(),
            calculateGrossAmount(request.quantity(), request.pricePerUnit()),
            request.transactionDate(),
            breakdown.fee(),
            request.notes(),
            null,
            assetName,
            request.exchange(),
            request.broker(),
            request.currency(),
            null,
            null,
            null,
            Boolean.FALSE);

    String scope = request.buy() ? CREATE_BUY_SCOPE : CREATE_SELL_SCOPE;
    TransactionResponse response =
        registerWithIdempotency(
            userId, scope, legacy, idempotencyKey, "Create imported stock transaction");

    ImportSource source =
        switch (request.brokerKind()) {
          case DRIVEWEALTH -> ImportSource.DRIVEWEALTH;
          case GBM_MX_EQUITY -> ImportSource.GBM_EQUITY;
        };
    transactionRepository
        .findByTransactionIdAndUserId(response.transactionId(), userId)
        .ifPresent(
            transaction -> {
              transaction.applyFriction(breakdown);
              transaction.setImportSource(source);
              transactionRepository.save(transaction);
            });

    return response;
  }

  @Override
  public void tagImportSource(UUID userId, UUID transactionId, TransactionOrigin origin) {
    ImportSource source = toImportSource(origin);
    transactionRepository
        .findByTransactionIdAndUserId(transactionId, userId)
        .ifPresent(
            transaction -> {
              transaction.setImportSource(source);
              transactionRepository.save(transaction);
            });
  }

  private ImportSource toImportSource(TransactionOrigin origin) {
    return switch (origin) {
      case MANUAL -> ImportSource.MANUAL;
      case DRIVEWEALTH -> ImportSource.DRIVEWEALTH;
      case GBM_STATEMENT -> ImportSource.GBM_STATEMENT;
      case GBM_EQUITY -> ImportSource.GBM_EQUITY;
    };
  }

  @Override
  @Transactional
  public TransactionResponse registerDividendTransaction(
      UUID userId, DividendTransactionRequest request, String idempotencyKey) {
    return transactionIdempotencyService.executeForTransactionResponse(
        userId,
        "transactions:dividend",
        idempotencyKey,
        request,
        () -> {
          try {
            String resolvedAssetName =
                (request.assetName() == null || request.assetName().isBlank())
                    ? assetCatalogQueryPort.findNameBySymbol(request.assetSymbol()).orElse(null)
                    : request.assetName();

            TransactionRequest txRequest =
                new TransactionRequest(
                    request.assetSymbol().toUpperCase(),
                    normalizedAssetType(request.assetType()),
                    "DIVIDEND",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    request.amount(),
                    request.transactionDate(),
                    BigDecimal.ZERO,
                    null,
                    null,
                    resolvedAssetName,
                    request.exchange(),
                    request.broker(),
                    request.currency(),
                    null,
                    null,
                    null,
                    null);

            TransactionResponse response =
                transactionRegistrationPort.registerTransaction(userId, txRequest);

            DividendType dtype = resolveDividendType(request.dividendType());

            Transaction savedTx =
                transactionRepository
                    .findByTransactionIdAndUserId(response.transactionId(), userId)
                    .orElseThrow(() -> new RuntimeException("Dividend transaction not found"));

            DividendDetail detail =
                DividendDetail.builder()
                    .transaction(savedTx)
                    .exDividendDate(request.exDividendDate())
                    .dividendType(dtype)
                    .taxWithheld(request.taxWithheld())
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            dividendDetailRepository.save(detail);

            transactionAuditPort.logCreateSuccess(
                userId, describeSuccessfulMutation("Create dividend transaction", response));
            return response;
          } catch (RuntimeException ex) {
            log.error(
                "Error creating dividend transaction for user {}: {}", userId, ex.getMessage(), ex);
            transactionAuditPort.logCreateFailure(
                userId,
                String.format(
                    "Create dividend transaction failed asset=%s reason=%s",
                    request.assetSymbol(), ex.getMessage()));
            throw ex;
          }
        });
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
            transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

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
    } else if ("REDEEM".equalsIgnoreCase(transaction.getTransactionType())) {
      // faceValue persisted in totalValue at redemption time
      netAmount = grossAmount;
      amountLabel = "Amount Redeemed";
    } else if ("DIVIDEND".equalsIgnoreCase(transaction.getTransactionType())) {
      netAmount = grossAmount.subtract(fee);
      amountLabel = "Dividend Received";
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
        transaction.getImportSource() != null ? transaction.getImportSource().name() : "MANUAL",
        null,
        "COMPLETED",
        buildFrictionBreakdown(transaction, grossAmount, fee, netAmount));
  }

  private FrictionBreakdownView buildFrictionBreakdown(
      Transaction transaction, BigDecimal grossAmount, BigDecimal fee, BigDecimal netAmount) {
    BigDecimal quantity = transaction.getQuantity();
    BigDecimal adjustedUnitPrice =
        (netAmount != null && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0)
            ? netAmount.divide(quantity, 8, RoundingMode.HALF_UP)
            : null;
    BigDecimal perUnitFriction =
        (fee != null && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0)
            ? fee.divide(quantity, 8, RoundingMode.HALF_UP)
            : null;
    return new FrictionBreakdownView(
        grossAmount,
        transaction.getBrokerCommission(),
        transaction.getBrokerIva(),
        transaction.getOtherFees(),
        fee,
        netAmount,
        adjustedUnitPrice,
        perUnitFriction,
        transaction.getReviewStatus());
  }

  public List<TransactionResponse> getTransactionsByUser(UUID userId) {
    return toResponsesWithLogos(transactionRepository.findByUserId(userId, RECENT_FIRST_SORT));
  }

  public List<TransactionResponse> getTransactionsByUserAndSymbol(UUID userId, String assetSymbol) {
    return toResponsesWithLogos(
        transactionRepository.findByUserIdAndAssetSymbol(userId, assetSymbol, RECENT_FIRST_SORT));
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
      return toResponsesWithLogos(
          transactionRepository.findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(
              userId, assetSymbol, normalizedAssetType, transactionType));
    }
    if (assetSymbol != null) {
      return toResponsesWithLogos(
          transactionRepository.findByUserIdAndAssetSymbol(userId, assetSymbol, RECENT_FIRST_SORT));
    }
    if (assetType != null) {
      return toResponsesWithLogos(
          transactionRepository.findByUserIdAndAssetType(
              userId, normalizedAssetType(assetType), RECENT_FIRST_SORT));
    }

    if (transactionType != null) {
      return toResponsesWithLogos(
          transactionRepository.findByUserIdAndTransactionType(
              userId, transactionType, RECENT_FIRST_SORT));
    }
    return toResponsesWithLogos(transactionRepository.findByUserId(userId, RECENT_FIRST_SORT));
  }

  /**
   * Resuelve los logos en batch para los símbolos distintos de la página, desde el catálogo
   * cacheado (Redis), y los aplica a cada fila. Reemplaza la resolución per-row vía proveedor
   * externo: N lookups por fila (con Finnhub inline, STOCK-only) → 1 lookup por símbolo distinto
   * desde el catálogo (crypto y stock), sin proveedores externos en el hot path.
   */
  private List<TransactionResponse> toResponsesWithLogos(List<Transaction> transactions) {
    Map<String, String> logos =
        new HashMap<>(
            assetCatalogQueryPort.findLogosBySymbols(
                transactions.stream()
                    .map(Transaction::getAssetSymbol)
                    .collect(Collectors.toSet())));
    if (iconPullOnceEnabled) {
      Map<String, String> candidates = distinctSymbolTypes(transactions);
      if (!candidates.isEmpty()) {
        // Resuelve/actualiza iconos desde los proveedores y los incluye en ESTA misma respuesta:
        // símbolos faltantes se resuelven una vez; iconos provisionales (fallback determinista) se
        // auto-actualizan al logo autoritativo (p.ej. CoinGecko). Ambos con caché y throttle en el
        // catálogo, así que no golpean al proveedor en cada lectura.
        logos.putAll(assetCatalogRefreshPort.resolveMissingIcons(candidates));
      }
    }
    return transactions.stream()
        .map(transaction -> toResponseWithLogo(transaction, logos))
        .toList();
  }

  /** Símbolos distintos vistos en la página, mapeados a su tipo (deduplicados). */
  private Map<String, String> distinctSymbolTypes(List<Transaction> transactions) {
    return transactions.stream()
        .filter(t -> t.getAssetSymbol() != null && !t.getAssetSymbol().isBlank())
        .collect(
            Collectors.toMap(
                t -> t.getAssetSymbol().toUpperCase(Locale.ROOT),
                t -> t.getAssetType() == null ? "" : t.getAssetType().name(),
                (a, b) -> a));
  }

  private TransactionResponse toResponseWithLogo(
      Transaction transaction, Map<String, String> logos) {
    TransactionResponse response = transactionMapper.toResponse(transaction);
    String symbol = transaction.getAssetSymbol();
    String logoUrl = symbol == null ? null : logos.get(symbol.toUpperCase(Locale.ROOT));
    return new TransactionResponse(
        response.transactionId(),
        response.assetSymbol(),
        response.assetType(),
        response.transactionType(),
        response.quantity(),
        response.pricePerUnit(),
        response.totalValue(),
        response.transactionDate(),
        response.fee(),
        response.notes(),
        response.createdAt(),
        response.updatedAt(),
        logoUrl,
        response.assetName(),
        response.exchange(),
        response.broker(),
        response.currency(),
        response.faceValue(),
        response.maturityDate(),
        response.couponRate(),
        response.autoReinvestment());
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
            transactionIdempotencyService.forgetByResultTransactionId(
                transaction.getTransactionId());
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
            // Pull-once: cataloga el icono del activo si aun no esta (background, no bloquea).
            if (iconPullOnceEnabled) {
              assetCatalogRefreshPort.ensureIconCatalogued(
                  response.assetSymbol(), response.assetType());
            }
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
    String assetName =
        (request.assetName() == null || request.assetName().isBlank())
            ? assetCatalogQueryPort.findNameBySymbol(request.assetSymbol()).orElse(null)
            : request.assetName();
    return new TransactionRequest(
        request.assetSymbol().toUpperCase(),
        normalizedAssetType(request.assetType()),
        "BUY",
        request.quantity(),
        request.pricePerUnit(),
        calculateGrossAmount(request.quantity(), request.pricePerUnit()),
        request.transactionDate(),
        resolveManualFee(
            request.fee(), request.brokerCommission(), request.brokerIva(), request.otherFees()),
        request.notes(),
        null,
        assetName,
        request.exchange(),
        request.broker(),
        request.currency(),
        request.faceValue(),
        request.maturityDate(),
        request.couponRate(),
        request.autoReinvestment());
  }

  private TransactionRequest toLegacyRequest(SellTransactionRequest request) {
    String assetName =
        (request.assetName() == null || request.assetName().isBlank())
            ? assetCatalogQueryPort.findNameBySymbol(request.assetSymbol()).orElse(null)
            : request.assetName();
    return new TransactionRequest(
        request.assetSymbol().toUpperCase(),
        normalizedAssetType(request.assetType()),
        "SELL",
        request.quantity(),
        request.pricePerUnit(),
        calculateGrossAmount(request.quantity(), request.pricePerUnit()),
        request.transactionDate(),
        resolveManualFee(
            request.fee(), request.brokerCommission(), request.brokerIva(), request.otherFees()),
        request.notes(),
        null,
        assetName,
        request.exchange(),
        request.broker(),
        request.currency(),
        request.faceValue(),
        request.maturityDate(),
        request.couponRate(),
        request.autoReinvestment());
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

  private DividendType resolveDividendType(String dividendType) {
    if (dividendType == null) {
      return DividendType.CASH;
    }
    if (dividendType.isBlank()) {
      throw new InvalidTransactionException("dividendType no puede estar en blanco");
    }
    try {
      return DividendType.valueOf(dividendType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidTransactionException("dividendType invalido: " + dividendType);
    }
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
