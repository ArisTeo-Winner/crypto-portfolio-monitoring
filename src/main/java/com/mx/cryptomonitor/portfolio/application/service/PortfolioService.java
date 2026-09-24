package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePoint;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioEntryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioTransactionCommand;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.exception.InsufficientFundsException;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioService implements PortfolioQueryPort, PortfolioEntryPort {
  private static final int MAX_RECONCILE_ATTEMPTS = 2;
  private static final int MONEY_SCALE = 2;
  private static final int COST_SCALE = 8;
  private static final Set<String> PORTFOLIO_TYPE_SCOPES =
      Set.of("CRYPTO", "STOCK", "ETF", "INDEX", "FIAT", "CASH");

  private final PortfolioEntryRepository portfolioEntryRepository;
  private final MarketDataProvider marketDataProvider;
  private final AssetPricePort assetPricePort;
  private final CryptoHistoricalPricePort cryptoHistoricalPricePort;
  private final AssetCatalogQueryPort assetCatalogQueryPort;
  private final TransactionHistoryPort transactionHistoryPort;
  private final FxRatePort fxRatePort;

  @Override
  public List<PortfolioEntry> getPortfolioEntriesByUser(UUID userId) {
    return loadPersistedPortfolioEntries(userId);
  }

  @Override
  public Optional<PortfolioEntry> getPortfolioEntryByUserAndSymbol(
      UUID userId, String assetSymbol) {
    return portfolioEntryRepository
        .findByUserIdAndAssetSymbol(userId, assetSymbol.toUpperCase())
        .map(entry -> sortPortfolioEntries(List.of(entry)).stream().findFirst().orElse(entry));
  }

  @Override
  public Optional<UUID> getPortfolioEntryIdByUserAndSymbol(UUID userId, String assetSymbol) {
    return getPortfolioEntryByUserAndSymbol(userId, assetSymbol)
        .map(PortfolioEntry::getPortfolioEntryId);
  }

  @Override
  public PortfolioHoldingsPerformanceResponse getHoldingsPerformanceByPortfolioId(
      UUID userId, String portfolioId, String period) {
    List<PortfolioTransactionSnapshot> snapshots =
        filterSnapshotsByPortfolioScope(
            transactionHistoryPort.getTransactionsByUser(userId), portfolioId);

    if (snapshots.isEmpty()) {
      return new PortfolioHoldingsPerformanceResponse(
          List.of(),
          false,
          scaleCurrency(BigDecimal.ZERO),
          scaleCurrency(BigDecimal.ZERO),
          scaleCurrency(BigDecimal.ZERO),
          null);
    }

    HoldingsPerformanceAccumulator accumulator = new HoldingsPerformanceAccumulator();
    accumulator.usdMxnRate = resolveUsdMxnRate();

    for (PortfolioTransactionSnapshot snapshot : snapshots) {
      PerformanceAssetState assetState =
          accumulator.assetsBySymbol.computeIfAbsent(
              snapshot.assetSymbol().toUpperCase(),
              ignored ->
                  new PerformanceAssetState(
                      snapshot.assetSymbol().toUpperCase(),
                      normalizeScopeAssetType(snapshot.assetType())));

      applyPerformanceSnapshot(accumulator, assetState, snapshot);
    }

    List<PortfolioHoldingsPerformanceResponse.SeriesPoint> series =
        buildHoldingsSeries(snapshots, accumulator.assetsBySymbol, period);

    BigDecimal currentValue = calculateMarkedValue(accumulator.assetsBySymbol, true);
    addOrReplaceSeriesPoint(series, now(), currentValue);

    BigDecimal costBasis =
        scaleCurrency(
            accumulator.assetsBySymbol.values().stream()
                .map(assetState -> assetState.totalBuyCostBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    BigDecimal unrealizedProfit =
        calculateUnrealizedProfit(accumulator.assetsBySymbol, currentValue);
    BigDecimal allTimeProfit = scaleCurrency(accumulator.realizedProfit.add(unrealizedProfit));
    BigDecimal allTimeProfitPercent = calculateAllTimeProfitPercent(allTimeProfit, costBasis);
    LocalDate firstTransactionDate = snapshots.getFirst().transactionDate().toLocalDate();
    List<PortfolioHoldingsPerformanceResponse.SeriesPoint> filteredSeries =
        filterSeriesByPeriod(series, period);
    boolean isProfit =
        filteredSeries.size() > 1
            && filteredSeries
                    .get(filteredSeries.size() - 1)
                    .value()
                    .compareTo(filteredSeries.getFirst().value())
                > 0;

    return new PortfolioHoldingsPerformanceResponse(
        filteredSeries,
        isProfit,
        allTimeProfit,
        allTimeProfitPercent,
        costBasis,
        firstTransactionDate);
  }

  @Override
  public Optional<BigDecimal> getCurrentCryptoPrice(String assetSymbol) {
    return assetPricePort.getCryptoPriceAmount(assetSymbol).blockOptional();
  }

  @Override
  public LocalDateTime now() {
    return LocalDateTime.now();
  }

  @Override
  @Transactional
  public UUID applyTransaction(UUID userId, PortfolioTransactionCommand command) {
    PortfolioEntry portfolioEntry = getOrCreatePortfolioEntry(userId, command);
    updatePortfolioEntry(portfolioEntry, command);
    return portfolioEntryRepository.save(portfolioEntry).getPortfolioEntryId();
  }

  @Override
  public void reconcilePortfolio(UUID userId) {
    reconcilePortfolioProjectionWithRetry(userId);
  }

  protected List<PortfolioEntry> reconcilePortfolioProjectionWithRetry(UUID userId) {
    ObjectOptimisticLockingFailureException lastFailure = null;

    for (int attempt = 1; attempt <= MAX_RECONCILE_ATTEMPTS; attempt++) {
      try {
        return reconcilePortfolioProjection(userId);
      } catch (ObjectOptimisticLockingFailureException ex) {
        lastFailure = ex;
        log.warn(
            "Conflicto optimista al reconciliar portfolio de usuario {}. Reintentando {}/{}.",
            userId,
            attempt,
            MAX_RECONCILE_ATTEMPTS);
      }
    }

    log.warn(
        "Se devolvera la proyeccion persistida del portfolio para {} tras agotar los reintentos de reconciliacion. Ultimo conflicto: {}",
        userId,
        lastFailure != null ? lastFailure.getClass().getSimpleName() : "desconocido");
    return loadPersistedPortfolioEntries(userId);
  }

  protected List<PortfolioEntry> reconcilePortfolioProjection(UUID userId) {
    List<PortfolioTransactionSnapshot> transactions =
        transactionHistoryPort.getTransactionsByUser(userId);
    List<PortfolioEntry> existingEntries = portfolioEntryRepository.findByUserId(userId);

    if (transactions.isEmpty()) {
      if (!existingEntries.isEmpty()) {
        portfolioEntryRepository.deleteAllInBatch(existingEntries);
      }
      return List.of();
    }

    Map<String, PortfolioEntry> existingBySymbol =
        existingEntries.stream()
            .collect(
                Collectors.toMap(
                    entry -> entry.getAssetSymbol().toUpperCase(),
                    entry -> entry,
                    (left, right) -> left));

    Map<String, List<PortfolioTransactionSnapshot>> transactionsBySymbol =
        transactions.stream()
            .collect(
                Collectors.groupingBy(
                    snapshot -> snapshot.assetSymbol().toUpperCase(),
                    LinkedHashMap::new,
                    Collectors.toList()));

    List<PortfolioEntry> rebuiltEntries = new ArrayList<>();
    List<PortfolioEntry> entriesToPersist = new ArrayList<>();
    BigDecimal usdMxnRate = resolveUsdMxnRate();

    transactionsBySymbol.forEach(
        (assetSymbol, snapshots) -> {
          PortfolioEntry existingEntry = existingBySymbol.get(assetSymbol);
          PortfolioProjectionState originalState = PortfolioProjectionState.from(existingEntry);
          PortfolioEntry rebuiltEntry =
              rebuildPortfolioEntry(userId, existingEntry, snapshots, usdMxnRate);

          if (rebuiltEntry.getTotalQuantity().compareTo(BigDecimal.ZERO) > 0) {
            rebuiltEntries.add(rebuiltEntry);
            if (existingEntry == null || hasProjectionChanged(originalState, rebuiltEntry)) {
              rebuiltEntry.setUpdatedAt(LocalDateTime.now());
              entriesToPersist.add(rebuiltEntry);
            }
          }
        });

    Map<String, PortfolioEntry> rebuiltBySymbol =
        rebuiltEntries.stream()
            .collect(Collectors.toMap(PortfolioEntry::getAssetSymbol, entry -> entry));

    List<PortfolioEntry> staleEntries =
        existingEntries.stream()
            .filter(
                existing -> !rebuiltBySymbol.containsKey(existing.getAssetSymbol().toUpperCase()))
            .toList();

    if (!staleEntries.isEmpty()) {
      portfolioEntryRepository.deleteAllInBatch(staleEntries);
    }

    if (!entriesToPersist.isEmpty()) {
      portfolioEntryRepository.saveAll(entriesToPersist);
    }

    return sortPortfolioEntries(rebuiltEntries);
  }

  private List<PortfolioEntry> loadPersistedPortfolioEntries(UUID userId) {
    return sortPortfolioEntries(portfolioEntryRepository.findByUserId(userId));
  }

  private List<PortfolioEntry> sortPortfolioEntries(List<PortfolioEntry> entries) {
    return entries.stream()
        .sorted(
            Comparator.comparing(
                    PortfolioEntry::getCurrentValue, Comparator.nullsLast(BigDecimal::compareTo))
                .reversed()
                .thenComparing(PortfolioEntry::getAssetSymbol))
        .toList();
  }

  private PortfolioEntry rebuildPortfolioEntry(
      UUID userId,
      PortfolioEntry existingEntry,
      List<PortfolioTransactionSnapshot> snapshots,
      BigDecimal usdMxnRate) {
    Optional<BigDecimal> persistedPriceFallback = derivePersistedUnitPrice(existingEntry);

    PortfolioEntry entry =
        existingEntry != null
            ? existingEntry
            : PortfolioEntry.builder()
                .userId(userId)
                .assetSymbol(snapshots.getFirst().assetSymbol().toUpperCase())
                .build();

    entry.setUserId(userId);
    entry.setAssetSymbol(snapshots.getFirst().assetSymbol().toUpperCase());
    entry.setAssetType(snapshots.getLast().assetType());
    entry.setTotalQuantity(BigDecimal.ZERO);
    entry.setTotalInvested(BigDecimal.ZERO);
    entry.setAveragePricePerUnit(BigDecimal.ZERO);

    for (PortfolioTransactionSnapshot snapshot : snapshots) {
      applySnapshot(entry, snapshot, usdMxnRate);
    }

    BigDecimal currentPrice =
        resolveCurrentAssetPrice(entry.getAssetSymbol(), entry.getAssetType())
            .or(
                () ->
                    Optional.ofNullable(
                        entry.getLastTransactionPrice())) // Usar último precio como 1er fallback
            .or(() -> persistedPriceFallback)
            .orElse(BigDecimal.ZERO);

    entry.setCurrentValue(
        entry.getTotalQuantity().multiply(currentPrice).setScale(2, RoundingMode.HALF_UP));
    entry.setTotalProfitLoss(
        entry
            .getCurrentValue()
            .subtract(entry.getTotalInvested())
            .setScale(2, RoundingMode.HALF_UP));

    return entry;
  }

  private boolean hasProjectionChanged(PortfolioProjectionState original, PortfolioEntry rebuilt) {
    return original == null || !original.matches(rebuilt);
  }

  private static boolean sameText(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }

  private static boolean sameAmount(BigDecimal left, BigDecimal right) {
    if (left == null) {
      return right == null;
    }
    if (right == null) {
      return false;
    }
    return left.compareTo(right) == 0;
  }

  private record PortfolioProjectionState(
      UUID userId,
      String assetSymbol,
      String assetType,
      BigDecimal totalQuantity,
      BigDecimal totalInvested,
      BigDecimal averagePricePerUnit,
      BigDecimal lastTransactionPrice,
      BigDecimal currentValue,
      BigDecimal totalProfitLoss) {

    static PortfolioProjectionState from(PortfolioEntry entry) {
      if (entry == null) {
        return null;
      }
      return new PortfolioProjectionState(
          entry.getUserId(),
          entry.getAssetSymbol(),
          entry.getAssetType(),
          entry.getTotalQuantity(),
          entry.getTotalInvested(),
          entry.getAveragePricePerUnit(),
          entry.getLastTransactionPrice(),
          entry.getCurrentValue(),
          entry.getTotalProfitLoss());
    }

    boolean matches(PortfolioEntry entry) {
      return sameText(userId, entry.getUserId())
          && sameText(assetSymbol, entry.getAssetSymbol())
          && sameText(assetType, entry.getAssetType())
          && sameAmount(totalQuantity, entry.getTotalQuantity())
          && sameAmount(totalInvested, entry.getTotalInvested())
          && sameAmount(averagePricePerUnit, entry.getAveragePricePerUnit())
          && sameAmount(lastTransactionPrice, entry.getLastTransactionPrice())
          && sameAmount(currentValue, entry.getCurrentValue())
          && sameAmount(totalProfitLoss, entry.getTotalProfitLoss());
    }
  }

  private void applySnapshot(
      PortfolioEntry entry, PortfolioTransactionSnapshot snapshot, BigDecimal usdMxnRate) {
    // Normaliza a base USD el costo y el precio de operaciones MXN, para que totalInvested y el P&L
    // de la entry (que consume /me/portfolio) no comparen pesos contra un valor en USD (ADR-0006).
    BigDecimal totalValue = toBaseCurrency(snapshot.totalValue(), snapshot.currency(), usdMxnRate);
    BigDecimal unitPrice = toBaseCurrency(snapshot.pricePerUnit(), snapshot.currency(), usdMxnRate);
    PortfolioTransactionCommand command =
        new PortfolioTransactionCommand(
            snapshot.assetSymbol().toUpperCase(),
            snapshot.assetType(),
            snapshot.transactionType(),
            snapshot.transferType(),
            snapshot.quantity(),
            totalValue,
            unitPrice);

    if ("BUY".equalsIgnoreCase(snapshot.transactionType())) {
      BigDecimal newQuantity = entry.getTotalQuantity().add(snapshot.quantity());
      BigDecimal newInvested = entry.getTotalInvested().add(totalValue);
      entry.setTotalQuantity(newQuantity);
      entry.setTotalInvested(newInvested);
      entry.setAveragePricePerUnit(
          newQuantity.compareTo(BigDecimal.ZERO) > 0
              ? newInvested.divide(newQuantity, 8, RoundingMode.HALF_UP)
              : BigDecimal.ZERO);
    } else if ("SELL".equalsIgnoreCase(snapshot.transactionType())) {
      if (entry.getTotalQuantity().compareTo(snapshot.quantity()) < 0) {
        entry.setTotalQuantity(BigDecimal.ZERO);
        entry.setTotalInvested(BigDecimal.ZERO);
        entry.setAveragePricePerUnit(BigDecimal.ZERO);
      } else {
        BigDecimal costBasisReduction =
            entry.getAveragePricePerUnit().multiply(snapshot.quantity());
        BigDecimal newQuantity = entry.getTotalQuantity().subtract(snapshot.quantity());
        BigDecimal newInvested = entry.getTotalInvested().subtract(costBasisReduction);
        entry.setTotalQuantity(newQuantity);
        if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
          entry.setTotalInvested(newInvested);
          entry.setAveragePricePerUnit(newInvested.divide(newQuantity, 8, RoundingMode.HALF_UP));
        } else {
          entry.setTotalInvested(BigDecimal.ZERO);
          entry.setAveragePricePerUnit(BigDecimal.ZERO);
        }
      }
    } else if ("TRANSFER".equalsIgnoreCase(snapshot.transactionType())) {
      applyTransfer(entry, command);
    }

    entry.setLastTransactionPrice(unitPrice);
  }

  private PortfolioEntry getOrCreatePortfolioEntry(
      UUID userId, PortfolioTransactionCommand command) {
    return portfolioEntryRepository
        .findByUserIdAndAssetSymbol(userId, command.assetSymbol())
        .orElseGet(
            () ->
                PortfolioEntry.builder()
                    .userId(userId)
                    .assetSymbol(command.assetSymbol())
                    .assetType(command.assetType())
                    .totalQuantity(BigDecimal.ZERO)
                    .totalInvested(BigDecimal.ZERO)
                    .averagePricePerUnit(BigDecimal.ZERO)
                    .build());
  }

  private void updatePortfolioEntry(PortfolioEntry entry, PortfolioTransactionCommand command) {
    if ("BUY".equalsIgnoreCase(command.transactionType())) {
      BigDecimal newQuantity = entry.getTotalQuantity().add(command.quantity());
      BigDecimal newInvested = entry.getTotalInvested().add(command.totalValue());
      entry.setTotalQuantity(newQuantity);
      entry.setTotalInvested(newInvested);
      entry.setAveragePricePerUnit(newInvested.divide(newQuantity, 8, RoundingMode.HALF_UP));
    } else if ("SELL".equalsIgnoreCase(command.transactionType())) {
      if (entry.getTotalQuantity().compareTo(command.quantity()) < 0) {
        throw new InsufficientFundsException("Cantidad insuficiente para vender");
      }
      BigDecimal costBasisReduction = entry.getAveragePricePerUnit().multiply(command.quantity());
      BigDecimal newQuantity = entry.getTotalQuantity().subtract(command.quantity());
      BigDecimal newInvested = entry.getTotalInvested().subtract(costBasisReduction);
      entry.setTotalQuantity(newQuantity);
      if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
        entry.setTotalInvested(newInvested);
        entry.setAveragePricePerUnit(newInvested.divide(newQuantity, 8, RoundingMode.HALF_UP));
      } else {
        entry.setTotalInvested(BigDecimal.ZERO);
        entry.setAveragePricePerUnit(BigDecimal.ZERO);
      }
    } else if ("TRANSFER".equalsIgnoreCase(command.transactionType())) {
      applyTransfer(entry, command);
    }

    BigDecimal price =
        resolveCurrentAssetPrice(command.assetSymbol(), command.assetType())
            .or(() -> Optional.ofNullable(command.pricePerUnit()))
            .or(() -> derivePersistedUnitPrice(entry))
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "No se pudo obtener el precio actual del activo en updatePortfolioEntry()"));

    BigDecimal currentValue =
        entry.getTotalQuantity().multiply(price).setScale(2, RoundingMode.HALF_UP);

    BigDecimal totalProfitLoss =
        currentValue
            .subtract(entry.getTotalInvested() != null ? entry.getTotalInvested() : BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);

    entry.setLastTransactionPrice(command.pricePerUnit());
    entry.setCurrentValue(currentValue);
    entry.setTotalProfitLoss(totalProfitLoss);
    entry.setUpdatedAt(LocalDateTime.now());
  }

  private void applyTransfer(PortfolioEntry entry, PortfolioTransactionCommand command) {
    BigDecimal currentQuantity = entry.getTotalQuantity();
    BigDecimal newQuantity;
    BigDecimal currentInvested =
        entry.getTotalInvested() != null ? entry.getTotalInvested() : BigDecimal.ZERO;
    BigDecimal averagePrice =
        entry.getAveragePricePerUnit() != null ? entry.getAveragePricePerUnit() : BigDecimal.ZERO;

    if ("TRANSFER_OUT".equalsIgnoreCase(command.transferType())) {
      if (currentQuantity.compareTo(command.quantity()) < 0) {
        throw new InsufficientFundsException("Cantidad insuficiente para transferir");
      }
      newQuantity = currentQuantity.subtract(command.quantity());
      BigDecimal transferredCostBasis = averagePrice.multiply(command.quantity());
      BigDecimal newInvested = currentInvested.subtract(transferredCostBasis);
      if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
        entry.setTotalInvested(BigDecimal.ZERO);
        entry.setAveragePricePerUnit(BigDecimal.ZERO);
      } else {
        entry.setTotalInvested(newInvested);
        entry.setAveragePricePerUnit(newInvested.divide(newQuantity, 8, RoundingMode.HALF_UP));
      }
    } else {
      newQuantity = currentQuantity.add(command.quantity());
      if (currentQuantity.compareTo(BigDecimal.ZERO) > 0
          && averagePrice.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal transferredCostBasis = averagePrice.multiply(command.quantity());
        BigDecimal newInvested = currentInvested.add(transferredCostBasis);
        entry.setTotalInvested(newInvested);
        entry.setAveragePricePerUnit(newInvested.divide(newQuantity, 8, RoundingMode.HALF_UP));
      }
    }

    entry.setTotalQuantity(newQuantity);
    if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
      entry.setTotalInvested(BigDecimal.ZERO);
      entry.setAveragePricePerUnit(BigDecimal.ZERO);
    } else if (entry.getTotalInvested().compareTo(BigDecimal.ZERO) > 0
        && entry.getAveragePricePerUnit().compareTo(BigDecimal.ZERO) == 0) {
      entry.setAveragePricePerUnit(
          entry.getTotalInvested().divide(newQuantity, 8, RoundingMode.HALF_UP));
    }
  }

  private Optional<BigDecimal> derivePersistedUnitPrice(PortfolioEntry entry) {
    if (entry == null) {
      return Optional.empty();
    }
    if (entry.getTotalQuantity() != null
        && entry.getTotalQuantity().compareTo(BigDecimal.ZERO) > 0) {
      if (entry.getCurrentValue() != null) {
        return Optional.of(
            entry.getCurrentValue().divide(entry.getTotalQuantity(), 8, RoundingMode.HALF_UP));
      }
      if (entry.getLastTransactionPrice() != null) {
        return Optional.of(entry.getLastTransactionPrice());
      }
    }
    return Optional.ofNullable(entry.getLastTransactionPrice());
  }

  private Optional<BigDecimal> resolveCurrentAssetPrice(String assetSymbol, String assetType) {
    try {
      if ("CRYPTO".equalsIgnoreCase(assetType)) {
        return assetPricePort.getCryptoPriceAmount(assetSymbol).blockOptional();
      }
      return marketDataProvider.getLatest(assetSymbol);
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo refrescar el precio actual de {} ({}). Se reutilizara el ultimo valor persistido si existe.",
          assetSymbol,
          assetType,
          ex);
      return Optional.empty();
    }
  }

  private List<PortfolioTransactionSnapshot> filterSnapshotsByPortfolioScope(
      List<PortfolioTransactionSnapshot> snapshots, String portfolioId) {
    String normalizedScope = normalizePortfolioScope(portfolioId);
    if (normalizedScope == null) {
      return snapshots;
    }

    if (PORTFOLIO_TYPE_SCOPES.contains(normalizedScope)) {
      return snapshots.stream()
          .filter(snapshot -> normalizeScopeAssetType(snapshot.assetType()).equals(normalizedScope))
          .toList();
    }

    return snapshots.stream()
        .filter(snapshot -> snapshot.assetSymbol().equalsIgnoreCase(normalizedScope))
        .toList();
  }

  private BigDecimal resolveUsdMxnRate() {
    return fxRatePort.usdMxnRate().orElse(null);
  }

  // Normaliza a la moneda base del portafolio (USD). v1 (ADR-0006): convierte MXN->USD con la tasa
  // actual; USD/null quedan igual. Sin tasa disponible => no convierte (comportamiento previo).
  private static BigDecimal toBaseCurrency(
      BigDecimal amount, String currency, BigDecimal usdMxnRate) {
    BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
    if (usdMxnRate == null || usdMxnRate.signum() <= 0 || !"MXN".equalsIgnoreCase(currency)) {
      return value;
    }
    return value.divide(usdMxnRate, COST_SCALE, RoundingMode.HALF_UP);
  }

  private void applyPerformanceSnapshot(
      HoldingsPerformanceAccumulator accumulator,
      PerformanceAssetState assetState,
      PortfolioTransactionSnapshot snapshot) {
    BigDecimal quantity = normalizeAmount(snapshot.quantity());
    BigDecimal rate = accumulator.usdMxnRate;
    String currency = snapshot.currency();
    BigDecimal rawUnitPrice = normalizeAmount(snapshot.pricePerUnit());
    BigDecimal unitPrice = toBaseCurrency(rawUnitPrice, currency, rate);
    BigDecimal fee = toBaseCurrency(normalizeAmount(snapshot.fee()), currency, rate);
    BigDecimal grossAmount =
        toBaseCurrency(resolveGrossAmount(snapshot, quantity, rawUnitPrice), currency, rate);
    String transactionType = snapshot.transactionType().trim().toUpperCase();

    if ("BUY".equals(transactionType)) {
      assetState.holdings = assetState.holdings.add(quantity);
      assetState.openCostBasis = assetState.openCostBasis.add(grossAmount).add(fee);
      assetState.totalBoughtQuantity = assetState.totalBoughtQuantity.add(quantity);
      assetState.totalBuyCostBasis = assetState.totalBuyCostBasis.add(grossAmount).add(fee);
      assetState.lastKnownPrice = resolveKnownPrice(unitPrice, assetState.lastKnownPrice);
      return;
    }

    if ("SELL".equals(transactionType)) {
      if (assetState.holdings.compareTo(BigDecimal.ZERO) <= 0) {
        assetState.lastKnownPrice = resolveKnownPrice(unitPrice, assetState.lastKnownPrice);
        return;
      }

      BigDecimal amountSold = min(quantity, assetState.holdings);
      BigDecimal averageBuyCost =
          calculateAverageBuyCost(assetState.totalBoughtQuantity, assetState.totalBuyCostBasis);
      BigDecimal realizedProfit =
          grossAmount.subtract(averageBuyCost.multiply(amountSold)).subtract(fee);

      accumulator.realizedProfit = accumulator.realizedProfit.add(realizedProfit);
      assetState.holdings = assetState.holdings.subtract(amountSold);
      assetState.openCostBasis =
          clampToZero(assetState.openCostBasis.subtract(averageBuyCost.multiply(amountSold)));
      assetState.lastKnownPrice = resolveKnownPrice(unitPrice, assetState.lastKnownPrice);

      if (assetState.holdings.compareTo(BigDecimal.ZERO) == 0) {
        assetState.openCostBasis = BigDecimal.ZERO;
      }
      return;
    }

    if ("TRANSFER".equals(transactionType)) {
      applyTransferToPerformanceState(assetState, snapshot.transferType(), quantity, unitPrice);
      return;
    }

    assetState.lastKnownPrice = resolveKnownPrice(unitPrice, assetState.lastKnownPrice);
  }

  private void applyTransferToPerformanceState(
      PerformanceAssetState assetState,
      String transferType,
      BigDecimal quantity,
      BigDecimal unitPrice) {
    BigDecimal averageBuyCost =
        calculateAverageBuyCost(assetState.totalBoughtQuantity, assetState.totalBuyCostBasis);

    if ("TRANSFER_OUT".equalsIgnoreCase(transferType)) {
      BigDecimal amountTransferred = min(quantity, assetState.holdings);
      assetState.holdings = assetState.holdings.subtract(amountTransferred);
      assetState.openCostBasis =
          clampToZero(
              assetState.openCostBasis.subtract(averageBuyCost.multiply(amountTransferred)));
      if (assetState.holdings.compareTo(BigDecimal.ZERO) == 0) {
        assetState.openCostBasis = BigDecimal.ZERO;
      }
    } else {
      BigDecimal transferCost = resolveKnownPrice(unitPrice, averageBuyCost).multiply(quantity);
      assetState.holdings = assetState.holdings.add(quantity);
      assetState.openCostBasis = assetState.openCostBasis.add(transferCost);
    }

    assetState.lastKnownPrice = resolveKnownPrice(unitPrice, assetState.lastKnownPrice);
  }

  private BigDecimal calculateMarkedValue(
      Map<String, PerformanceAssetState> assetsBySymbol, boolean useCurrentQuotes) {
    BigDecimal total =
        assetsBySymbol.values().stream()
            .map(
                assetState -> {
                  BigDecimal referencePrice =
                      useCurrentQuotes
                          ? resolveCurrentAssetPrice(assetState.assetSymbol, assetState.assetType)
                              .orElse(assetState.lastKnownPrice)
                          : assetState.lastKnownPrice;
                  return assetState.holdings.multiply(normalizeAmount(referencePrice));
                })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return scaleCurrency(total);
  }

  private List<PortfolioHoldingsPerformanceResponse.SeriesPoint> buildHoldingsSeries(
      List<PortfolioTransactionSnapshot> snapshots,
      Map<String, PerformanceAssetState> assetsBySymbol,
      String period) {
    Optional<List<PortfolioHoldingsPerformanceResponse.SeriesPoint>> historicalSeries =
        buildHistoricalCryptoSeries(snapshots, period);

    if (historicalSeries.isPresent()) {
      return historicalSeries.orElseThrow();
    }

    return buildSnapshotSeries(snapshots, assetsBySymbol);
  }

  private List<PortfolioHoldingsPerformanceResponse.SeriesPoint> buildSnapshotSeries(
      List<PortfolioTransactionSnapshot> snapshots,
      Map<String, PerformanceAssetState> assetsBySymbol) {
    HoldingsPerformanceAccumulator runningAccumulator = new HoldingsPerformanceAccumulator();
    runningAccumulator.usdMxnRate = resolveUsdMxnRate();
    List<PortfolioHoldingsPerformanceResponse.SeriesPoint> series = new ArrayList<>();

    for (PortfolioTransactionSnapshot snapshot : snapshots) {
      PerformanceAssetState assetState =
          runningAccumulator.assetsBySymbol.computeIfAbsent(
              snapshot.assetSymbol().toUpperCase(),
              ignored ->
                  new PerformanceAssetState(
                      snapshot.assetSymbol().toUpperCase(),
                      normalizeScopeAssetType(snapshot.assetType())));

      applyPerformanceSnapshot(runningAccumulator, assetState, snapshot);
      addOrReplaceSeriesPoint(
          series,
          snapshot.transactionDate().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
          calculateMarkedValue(runningAccumulator.assetsBySymbol, false));
    }

    BigDecimal currentValue = calculateMarkedValue(assetsBySymbol, true);
    addOrReplaceSeriesPoint(series, now(), currentValue);
    return series;
  }

  private Optional<List<PortfolioHoldingsPerformanceResponse.SeriesPoint>>
      buildHistoricalCryptoSeries(List<PortfolioTransactionSnapshot> snapshots, String period) {
    boolean allCrypto =
        snapshots.stream()
            .allMatch(
                snapshot ->
                    "CRYPTO".equalsIgnoreCase(normalizeScopeAssetType(snapshot.assetType())));
    if (!allCrypto) {
      return Optional.empty();
    }

    Instant fromInclusive = snapshots.getFirst().transactionDate().toInstant();
    Instant toInclusive = now().toInstant(ZoneOffset.UTC);
    Integer historicalDays = resolveHistoricalPeriodDays(period);
    Instant historicalPeriodStart =
        historicalDays == null
            ? fromInclusive
            : toInclusive.minusSeconds((long) historicalDays * 24L * 60L * 60L);

    Map<String, List<PortfolioTransactionSnapshot>> snapshotsBySymbol =
        snapshots.stream()
            .collect(
                Collectors.groupingBy(
                    snapshot -> snapshot.assetSymbol().toUpperCase(),
                    LinkedHashMap::new,
                    Collectors.toList()));

    Map<String, List<CryptoHistoricalPricePoint>> historicalPointsBySymbol = new LinkedHashMap<>();
    List<Instant> timeline = new ArrayList<>();

    for (Map.Entry<String, List<PortfolioTransactionSnapshot>> entry :
        snapshotsBySymbol.entrySet()) {
      String assetSymbol = entry.getKey();
      String assetId =
          Optional.ofNullable(assetCatalogQueryPort.findAssetIdBySymbol(assetSymbol))
              .orElse(Optional.empty())
              .orElse(null);

      if (assetId == null) {
        log.info(
            "No se encontro assetId de CoinGecko para {}. Se usara la serie por snapshots.",
            assetSymbol);
        return Optional.empty();
      }

      try {
        var historicalSeries =
            (historicalDays == null
                    ? cryptoHistoricalPricePort.getHistoricalUsdPrices(
                        assetId, fromInclusive, toInclusive)
                    : cryptoHistoricalPricePort.getHistoricalUsdPrices(assetId, historicalDays))
                .blockOptional()
                .orElse(null);

        if (historicalSeries == null || historicalSeries.points().isEmpty()) {
          log.info(
              "CoinGecko no devolvio historico para {} ({}). Se usara la serie por snapshots.",
              assetSymbol,
              assetId);
          return Optional.empty();
        }

        historicalPointsBySymbol.put(assetSymbol, historicalSeries.points());
        historicalSeries.points().stream()
            .map(CryptoHistoricalPricePoint::timestamp)
            .forEach(timeline::add);
      } catch (RuntimeException ex) {
        log.warn(
            "No se pudo obtener historico CoinGecko para {}. Se usara la serie por snapshots.",
            assetSymbol,
            ex);
        return Optional.empty();
      }
    }

    snapshots.stream()
        .map(PortfolioTransactionSnapshot::transactionDate)
        .map(dateTime -> dateTime.toInstant())
        .filter(transactionInstant -> !transactionInstant.isBefore(historicalPeriodStart))
        .forEach(timeline::add);
    timeline.add(toInclusive);

    List<Instant> orderedTimeline = timeline.stream().distinct().sorted().toList();
    List<PortfolioHoldingsPerformanceResponse.SeriesPoint> series = new ArrayList<>();

    snapshotsBySymbol.forEach(
        (assetSymbol, assetSnapshots) ->
            assetSnapshots.sort(
                Comparator.comparing(PortfolioTransactionSnapshot::transactionDate)));

    for (Instant instant : orderedTimeline) {
      BigDecimal totalValue = BigDecimal.ZERO;

      for (Map.Entry<String, List<PortfolioTransactionSnapshot>> assetEntry :
          snapshotsBySymbol.entrySet()) {
        BigDecimal holdings = calculateHoldingsAtInstant(assetEntry.getValue(), instant);
        if (holdings.compareTo(BigDecimal.ZERO) <= 0) {
          continue;
        }

        BigDecimal price =
            instant.equals(toInclusive)
                ? resolveCurrentAssetPrice(assetEntry.getKey(), "CRYPTO")
                    .or(
                        () ->
                            resolveHistoricalPriceAtInstant(
                                historicalPointsBySymbol.get(assetEntry.getKey()), instant))
                    .orElse(BigDecimal.ZERO)
                : resolveHistoricalPriceAtInstant(
                        historicalPointsBySymbol.get(assetEntry.getKey()), instant)
                    .orElse(BigDecimal.ZERO);

        totalValue = totalValue.add(holdings.multiply(price));
      }

      addOrReplaceSeriesPoint(series, LocalDateTime.ofInstant(instant, ZoneOffset.UTC), totalValue);
    }

    return Optional.of(series);
  }

  private BigDecimal calculateHoldingsAtInstant(
      List<PortfolioTransactionSnapshot> snapshots, Instant instant) {
    BigDecimal holdings = BigDecimal.ZERO;

    for (PortfolioTransactionSnapshot snapshot : snapshots) {
      Instant transactionInstant = snapshot.transactionDate().toInstant();
      if (transactionInstant.isAfter(instant)) {
        break;
      }

      BigDecimal quantity = normalizeAmount(snapshot.quantity());
      String transactionType = snapshot.transactionType().trim().toUpperCase();

      if ("BUY".equals(transactionType)) {
        holdings = holdings.add(quantity);
      } else if ("SELL".equals(transactionType)) {
        holdings = clampToZero(holdings.subtract(quantity));
      } else if ("TRANSFER".equals(transactionType)) {
        if ("TRANSFER_OUT".equalsIgnoreCase(snapshot.transferType())) {
          holdings = clampToZero(holdings.subtract(quantity));
        } else {
          holdings = holdings.add(quantity);
        }
      }
    }

    return holdings;
  }

  private Optional<BigDecimal> resolveHistoricalPriceAtInstant(
      List<CryptoHistoricalPricePoint> points, Instant instant) {
    if (points == null || points.isEmpty()) {
      return Optional.empty();
    }

    CryptoHistoricalPricePoint selected = null;
    for (CryptoHistoricalPricePoint point : points) {
      if (point.timestamp().isAfter(instant)) {
        break;
      }
      selected = point;
    }

    if (selected == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(selected.priceUsd());
  }

  private BigDecimal calculateUnrealizedProfit(
      Map<String, PerformanceAssetState> assetsBySymbol, BigDecimal currentValue) {
    BigDecimal totalAverageCostOfHoldings =
        assetsBySymbol.values().stream()
            .map(
                assetState ->
                    calculateAverageBuyCost(
                            assetState.totalBoughtQuantity, assetState.totalBuyCostBasis)
                        .multiply(assetState.holdings))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return scaleCurrency(currentValue.subtract(totalAverageCostOfHoldings));
  }

  private void addOrReplaceSeriesPoint(
      List<PortfolioHoldingsPerformanceResponse.SeriesPoint> series,
      LocalDateTime dateTime,
      BigDecimal value) {
    long unixTimestamp = dateTime.toEpochSecond(ZoneOffset.UTC);
    PortfolioHoldingsPerformanceResponse.SeriesPoint point =
        new PortfolioHoldingsPerformanceResponse.SeriesPoint(unixTimestamp, scaleCurrency(value));

    if (!series.isEmpty() && series.get(series.size() - 1).time() == unixTimestamp) {
      series.set(series.size() - 1, point);
      return;
    }

    series.add(point);
  }

  private BigDecimal resolveGrossAmount(
      PortfolioTransactionSnapshot snapshot, BigDecimal quantity, BigDecimal unitPrice) {
    BigDecimal totalValue = normalizeAmount(snapshot.totalValue());
    if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
      return totalValue;
    }
    return quantity.multiply(unitPrice);
  }

  private BigDecimal calculateAverageBuyCost(BigDecimal holdings, BigDecimal openCostBasis) {
    if (holdings.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    return openCostBasis.divide(holdings, COST_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal scaleCurrency(BigDecimal amount) {
    return normalizeAmount(amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal clampToZero(BigDecimal amount) {
    BigDecimal normalized = normalizeAmount(amount);
    return normalized.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : normalized;
  }

  private BigDecimal resolveKnownPrice(BigDecimal preferredPrice, BigDecimal fallbackPrice) {
    BigDecimal normalizedPreferred = normalizeAmount(preferredPrice);
    if (normalizedPreferred.compareTo(BigDecimal.ZERO) > 0) {
      return normalizedPreferred;
    }
    return normalizeAmount(fallbackPrice);
  }

  private BigDecimal normalizeAmount(BigDecimal amount) {
    return amount != null ? amount : BigDecimal.ZERO;
  }

  private BigDecimal min(BigDecimal left, BigDecimal right) {
    return left.compareTo(right) <= 0 ? left : right;
  }

  private String normalizePortfolioScope(String portfolioId) {
    if (portfolioId == null || portfolioId.isBlank()) {
      return null;
    }

    String normalized = portfolioId.trim().toUpperCase();
    if ("OVERVIEW".equals(normalized) || "ALL".equals(normalized) || "DEFAULT".equals(normalized)) {
      return null;
    }
    if ("STOCKS".equals(normalized)) {
      return "STOCK";
    }
    if ("ETFS".equals(normalized)) {
      return "ETF";
    }
    return normalized;
  }

  private List<PortfolioHoldingsPerformanceResponse.SeriesPoint> filterSeriesByPeriod(
      List<PortfolioHoldingsPerformanceResponse.SeriesPoint> series, String period) {
    if (series.isEmpty()) {
      return series;
    }

    String normalizedPeriod = normalizeSeriesPeriod(period);
    if ("ALL".equals(normalizedPeriod) || normalizedPeriod.isBlank()) {
      return List.copyOf(series);
    }

    LocalDateTime cutoff =
        switch (normalizedPeriod) {
          case "24H" -> now().minusHours(24);
          case "7D" -> now().minusDays(7);
          case "30D" -> now().minusDays(30);
          case "90D" -> now().minusDays(90);
          default -> null;
        };

    if (cutoff == null) {
      return List.copyOf(series);
    }

    long cutoffEpoch = cutoff.toEpochSecond(ZoneOffset.UTC);
    List<PortfolioHoldingsPerformanceResponse.SeriesPoint> filtered =
        series.stream().filter(point -> point.time() >= cutoffEpoch).toList();

    if (filtered.isEmpty()) {
      return List.of(series.getLast());
    }

    return filtered;
  }

  private Integer resolveHistoricalPeriodDays(String period) {
    String normalizedPeriod = normalizeSeriesPeriod(period);
    return switch (normalizedPeriod) {
      case "24H" -> 1;
      case "7D" -> 7;
      case "30D" -> 30;
      case "90D" -> 90;
      default -> null;
    };
  }

  private String normalizeSeriesPeriod(String period) {
    if (period == null) {
      return "ALL";
    }
    String normalized = period.trim().toUpperCase();
    return normalized.isBlank() ? "ALL" : normalized;
  }

  private BigDecimal calculateAllTimeProfitPercent(BigDecimal allTimeProfit, BigDecimal costBasis) {
    if (costBasis.compareTo(BigDecimal.ZERO) <= 0) {
      return scaleCurrency(BigDecimal.ZERO);
    }

    return allTimeProfit
        .multiply(new BigDecimal("100"))
        .divide(costBasis, MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private String normalizeScopeAssetType(String assetType) {
    if (assetType == null) {
      return "";
    }
    String normalized = assetType.trim().toUpperCase();
    if ("STOCKS".equals(normalized)) {
      return "STOCK";
    }
    if ("ETFS".equals(normalized)) {
      return "ETF";
    }
    return normalized;
  }

  private static final class HoldingsPerformanceAccumulator {
    private final Map<String, PerformanceAssetState> assetsBySymbol = new LinkedHashMap<>();
    private BigDecimal realizedProfit = BigDecimal.ZERO;
    private BigDecimal usdMxnRate;
  }

  private static final class PerformanceAssetState {
    private final String assetSymbol;
    private final String assetType;
    private BigDecimal holdings = BigDecimal.ZERO;
    private BigDecimal openCostBasis = BigDecimal.ZERO;
    private BigDecimal totalBoughtQuantity = BigDecimal.ZERO;
    private BigDecimal totalBuyCostBasis = BigDecimal.ZERO;
    private BigDecimal lastKnownPrice = BigDecimal.ZERO;

    private PerformanceAssetState(String assetSymbol, String assetType) {
      this.assetSymbol = assetSymbol;
      this.assetType = assetType;
    }
  }
}
