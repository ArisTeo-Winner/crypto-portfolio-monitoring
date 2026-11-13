package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort.AssetDisplay;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogRefreshPort;
import com.mx.cryptomonitor.shared.event.TransactionRegisteredEvent;
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
import com.mx.cryptomonitor.transaction.application.port.in.DividendReconciliationUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionQueryUseCase;
import com.mx.cryptomonitor.transaction.application.port.out.PortfolioProjectionSyncPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionAuditPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionRegistrationPort;
import com.mx.cryptomonitor.transaction.domain.exception.ImportedTransactionNotEditableException;
import com.mx.cryptomonitor.transaction.domain.exception.InvalidTransactionException;
import com.mx.cryptomonitor.transaction.domain.exception.TransactionNotFoundException;
import com.mx.cryptomonitor.transaction.domain.friction.FrictionBreakdown;
import com.mx.cryptomonitor.transaction.domain.friction.FrictionSide;
import com.mx.cryptomonitor.transaction.domain.friction.GbmFrictionCalculator;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;
import com.mx.cryptomonitor.transaction.domain.model.DividendOrigin;
import com.mx.cryptomonitor.transaction.domain.model.DividendType;
import com.mx.cryptomonitor.transaction.domain.model.ImportSource;
import com.mx.cryptomonitor.transaction.domain.model.OperationTimestamps;
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

  /**
   * Tipos de transacción que NO son operaciones bursátiles sino ingresos (el accrual registra el
   * dividendo como una transacción DIVIDEND). El listado de transacciones los excluye por defecto
   * para que la vista muestre solo operaciones (compra/venta/transfer), como la pestaña "Trades" de
   * Snowball; los ingresos se consultan explícitamente con {@code ?transactionType=DIVIDEND}.
   */
  private static final Set<String> INCOME_TRANSACTION_TYPES = Set.of("DIVIDEND");

  private static final String CREATE_TRANSACTION_SCOPE = "transactions:create";
  private static final String CREATE_BUY_SCOPE = "transactions:buy";
  private static final String CREATE_SELL_SCOPE = "transactions:sell";
  private static final String CREATE_TRANSFER_SCOPE = "transactions:transfer";
  // ADR-0024: tipos de activo que pagan dividendos/distribuciones -> disparan el auto-poblado.
  private static final Set<String> DIVIDEND_ELIGIBLE_ASSET_TYPES = Set.of("STOCK", "ETF");

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
  private final ApplicationEventPublisher eventPublisher;
  private final DividendReconciliationUseCase dividendReconciliationUseCase;

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

  /**
   * Paridad de edicion con el alta (ADR-0005). Si el request trae desglose (comision/IVA/otros),
   * deriva {@code fee} = comision + IVA + otros, refresca el breakdown fino e ignora {@code fee}
   * plano. Si solo trae {@code fee}: cuando el monto cambio respecto al guardado, limpia el
   * desglose fino viejo (ya no cuadra); cuando es igual, lo conserva (edicion ajena a costos). El
   * desglose solo aplica a BUY/SELL.
   */
  private void applyFrictionOnUpdate(
      Transaction transaction, String transactionType, UpdateTransactionRequest request) {
    boolean hasSplit =
        request.brokerCommission() != null
            || request.brokerIva() != null
            || request.otherFees() != null;
    FrictionSide side = frictionSideFor(transactionType);
    if (hasSplit && side != null) {
      transaction.applyFriction(
          frictionCalculator.manual(
              side,
              request.quantity(),
              transaction.getPricePerUnit(),
              request.brokerCommission(),
              request.brokerIva(),
              request.otherFees(),
              null));
      return;
    }
    BigDecimal newFee = normalizedFee(request.fee());
    BigDecimal storedFee = transaction.getFee() != null ? transaction.getFee() : BigDecimal.ZERO;
    if (storedFee.compareTo(newFee) != 0) {
      transaction.clearFrictionBreakdown();
    }
    transaction.setFee(newFee);
  }

  private FrictionSide frictionSideFor(String transactionType) {
    return switch (transactionType) {
      case "BUY" -> FrictionSide.BUY;
      case "SELL" -> FrictionSide.SELL;
      default -> null;
    };
  }

  private static boolean isImported(ImportSource source) {
    return source != null && source != ImportSource.MANUAL;
  }

  private void guardImportedFinancialFieldsUnchanged(
      Transaction stored, UpdateTransactionRequest request) {
    boolean financialChange =
        !stored.getAssetSymbol().equalsIgnoreCase(request.assetSymbol().trim())
            || stored.getAssetType() != normalizedAssetType(request.assetType())
            || numberChanged(stored.getQuantity(), request.quantity())
            || numberChanged(stored.getPricePerUnit(), request.pricePerUnit())
            || instantChanged(stored.getTransactionDate(), request.transactionDate())
            || numberChanged(stored.getFee(), request.fee())
            || numberChanged(stored.getBrokerCommission(), request.brokerCommission())
            || numberChanged(stored.getBrokerIva(), request.brokerIva())
            || numberChanged(stored.getOtherFees(), request.otherFees());
    if (financialChange) {
      throw new ImportedTransactionNotEditableException(
          "La transaccion fue importada de "
              + stored.getImportSource().name()
              + " y es un registro de solo lectura del broker: solo se pueden editar las notas.");
    }
  }

  private static boolean numberChanged(BigDecimal stored, BigDecimal requested) {
    if (requested == null) {
      return false;
    }
    BigDecimal current = stored != null ? stored : BigDecimal.ZERO;
    return current.compareTo(requested) != 0;
  }

  private static boolean instantChanged(OffsetDateTime stored, OffsetDateTime requested) {
    if (stored == null || requested == null) {
      return stored != requested;
    }
    return !stored.toInstant().equals(requested.toInstant());
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
              // ADR-0013: fija el binomio de operacion (wall-clock + zona IANA) desde el import y
              // recalcula executed_at_utc. Si el import no lo trae, se deja para la migracion/F2b.
              if (request.localExecutionTime() != null && request.executionZoneId() != null) {
                OperationTimestamps.apply(
                    transaction, request.localExecutionTime(), request.executionZoneId());
              }
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
  public TransactionResponse registerDividendTransaction(
      UUID userId, DividendTransactionRequest request, String idempotencyKey) {
    return registerDividendTransaction(userId, request, idempotencyKey, DividendOrigin.MANUAL);
  }

  @Override
  @Transactional
  public TransactionResponse registerDividendTransaction(
      UUID userId,
      DividendTransactionRequest request,
      String idempotencyKey,
      DividendOrigin origin) {
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
                    .amountPerShare(request.amountPerShare())
                    .origin(origin)
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

            if (isImported(transaction.getImportSource())) {
              // Importado (DriveWealth/GBM): registro autoritativo del broker. Solo Notas es
              // editable; cualquier cambio financiero se rechaza (ver ADR-0007).
              guardImportedFinancialFieldsUnchanged(transaction, request);
              transaction.setNotes(request.notes());
              transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
              TransactionResponse notesOnly =
                  transactionMapper.toResponse(transactionRepository.save(transaction));
              transactionAuditPort.logUpdateSuccess(
                  userId,
                  describeSuccessfulMutation("Updated imported transaction notes", notesOnly));
              return notesOnly;
            }

            transaction.setAssetSymbol(request.assetSymbol().trim().toUpperCase(Locale.ROOT));
            transaction.setAssetType(normalizedAssetType(request.assetType()));
            transaction.setQuantity(request.quantity());
            transaction.setPricePerUnit(
                resolvePricePerUnit(transactionType, request.pricePerUnit()));
            transaction.setTotalValue(
                calculateTotalValue(
                    transactionType, request.quantity(), transaction.getPricePerUnit()));
            transaction.setTransactionDate(request.transactionDate());
            applyFrictionOnUpdate(transaction, transactionType, request);
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

    List<Transaction> transactions;
    if (assetSymbol != null && assetType != null && transactionType != null) {
      transactions =
          transactionRepository.findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(
              userId, assetSymbol, normalizedAssetType(assetType), transactionType);
    } else if (assetSymbol != null) {
      transactions =
          transactionRepository.findByUserIdAndAssetSymbol(userId, assetSymbol, RECENT_FIRST_SORT);
    } else if (assetType != null) {
      transactions =
          transactionRepository.findByUserIdAndAssetType(
              userId, normalizedAssetType(assetType), RECENT_FIRST_SORT);
    } else if (transactionType != null) {
      transactions =
          transactionRepository.findByUserIdAndTransactionType(
              userId, transactionType, RECENT_FIRST_SORT);
    } else {
      transactions = transactionRepository.findByUserId(userId, RECENT_FIRST_SORT);
    }

    // Sin filtro explícito de transactionType, el listado es SOLO operaciones: los ingresos (p. ej.
    // DIVIDEND, que el accrual registra como transacción) se excluyen para que la vista muestre
    // compra/venta/transfer y no dividendos. Los ingresos se piden con ?transactionType=DIVIDEND.
    if (transactionType == null) {
      transactions =
          transactions.stream()
              .filter(
                  t ->
                      !INCOME_TRANSACTION_TYPES.contains(
                          normalizeTransactionType(t.getTransactionType())))
              .toList();
    }

    return toResponsesWithLogos(transactions);
  }

  /**
   * Resuelve los logos en batch para los símbolos distintos de la página, desde el catálogo
   * cacheado (Redis), y los aplica a cada fila. Reemplaza la resolución per-row vía proveedor
   * externo: N lookups por fila (con Finnhub inline, STOCK-only) → 1 lookup por símbolo distinto
   * desde el catálogo (crypto y stock), sin proveedores externos en el hot path.
   */
  private List<TransactionResponse> toResponsesWithLogos(List<Transaction> transactions) {
    if (iconPullOnceEnabled) {
      Map<String, String> candidates = distinctSymbolTypes(transactions);
      if (!candidates.isEmpty()) {
        // Resuelve/actualiza en el catálogo (name + logo) los símbolos faltantes o provisionales:
        // símbolos nuevos se resuelven una vez; iconos provisionales (fallback determinista) se
        // auto-actualizan al logo autoritativo. Con caché y throttle en el catálogo, no golpean al
        // proveedor en cada lectura.
        assetCatalogRefreshPort.resolveMissingIcons(candidates);
      }
    }
    // ADR-0008: el catálogo es la única fuente de verdad de name + logo; se leen tras la
    // resolución,
    // en lugar de un valor denormalizado y congelado en la transacción.
    Map<String, AssetDisplay> display =
        new HashMap<>(
            assetCatalogQueryPort.findDisplayBySymbols(
                transactions.stream()
                    .map(Transaction::getAssetSymbol)
                    .collect(Collectors.toSet())));
    return transactions.stream()
        .map(transaction -> toResponseWithDisplay(transaction, display))
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

  private TransactionResponse toResponseWithDisplay(
      Transaction transaction, Map<String, AssetDisplay> display) {
    TransactionResponse response = transactionMapper.toResponse(transaction);
    String symbol = transaction.getAssetSymbol();
    AssetDisplay d = symbol == null ? null : display.get(symbol.toUpperCase(Locale.ROOT));
    String logoUrl = d != null ? d.logoUrl() : null;
    // El nombre viene del catálogo (fuente de verdad); si aún no está resuelto ahí, se cae al
    // snapshot persistido en la transacción (p.ej. el nombre reportado por el broker en el alta).
    String assetName =
        (d != null && d.name() != null && !d.name().isBlank()) ? d.name() : response.assetName();
    return new TransactionResponse(
        response.transactionId(),
        response.assetSymbol(),
        response.assetType(),
        response.transactionType(),
        response.quantity(),
        response.pricePerUnit(),
        response.totalValue(),
        response.transactionDate(),
        response.localExecutionTime(),
        response.executionZoneId(),
        response.executedAtUtc(),
        response.fee(),
        response.notes(),
        response.createdAt(),
        response.updatedAt(),
        logoUrl,
        assetName,
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
            // SPEC reconciliación: si se borró una operación que afecta tenencia (no un dividendo),
            // re-derivar los dividendos auto-acreditados del símbolo (elimina huérfanos /
            // recalcula).
            if (!"DIVIDEND".equalsIgnoreCase(transaction.getTransactionType())) {
              dividendReconciliationUseCase.reconcileAccruedDividends(
                  userId, transaction.getAssetSymbol());
            }
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
            // El registro incremental (applyTransaction) no normaliza FX: reconciliamos la
            // proyeccion desde las transacciones (fuente de verdad) para que el costo MXN quede
            // convertido a base USD con el FX de la fecha (ADR-0009), igual que en
            // updateTransaction.
            portfolioProjectionSyncPort.reconcileUserPortfolio(userId);
            transactionRealizedPnlService.rebuildUserRealizedPnl(userId);
            portfolioProjectionSyncPort.recordUserPortfolioSnapshot(userId);
            transactionAuditPort.logCreateSuccess(
                userId, describeSuccessfulMutation(actionLabel, response));
            // Pull-once: cataloga el icono del activo si aun no esta (background, no bloquea).
            if (iconPullOnceEnabled) {
              assetCatalogRefreshPort.ensureIconCatalogued(
                  response.assetSymbol(), response.assetType());
            }
            // ADR-0024: auto-poblado de dividendos. Publicado dentro de la transaccion para que el
            // listener de marketdata corra AFTER_COMMIT (async): no bloquea el registro ni lo
            // revierte si el proveedor falla.
            publishDividendSyncTriggerIfEligible(response);
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

  /**
   * ADR-0024: si la operación recién registrada es una COMPRA de un activo que paga dividendos
   * (STOCK/ETF), publica {@link TransactionRegisteredEvent} para que marketdata pueble su historial
   * de dividendos automáticamente. Idempotente aguas abajo; se omite en cualquier otro caso.
   */
  private void publishDividendSyncTriggerIfEligible(TransactionResponse response) {
    if (!"BUY".equalsIgnoreCase(response.transactionType())) {
      return;
    }
    String assetType = response.assetType();
    if (assetType == null
        || !DIVIDEND_ELIGIBLE_ASSET_TYPES.contains(assetType.toUpperCase(Locale.ROOT))) {
      return;
    }
    eventPublisher.publishEvent(new TransactionRegisteredEvent(response.assetSymbol(), assetType));
  }

  private void validateUpdateRequest(String transactionType, UpdateTransactionRequest request) {
    boolean hasSplit =
        request.brokerCommission() != null
            || request.brokerIva() != null
            || request.otherFees() != null;
    if (hasSplit && !("BUY".equals(transactionType) || "SELL".equals(transactionType))) {
      throw new InvalidTransactionException(
          "El desglose de comision/IVA solo aplica a transacciones BUY o SELL");
    }
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
