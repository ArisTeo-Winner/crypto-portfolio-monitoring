package com.mx.cryptomonitor.statementimport.application.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportGbmStatementUseCase;
import com.mx.cryptomonitor.statementimport.application.port.out.GbmStatementParserPort;
import com.mx.cryptomonitor.statementimport.domain.model.GbmStatementParseResult;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedStatementRow;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbmStatementImportService implements ImportGbmStatementUseCase {

  private static final String BROKER = "GBM";
  private static final String EXCHANGE_LOCAL = "BMV";
  private static final String CURRENCY_MXN = "MXN";
  private static final String FOREX_SYMBOL = "USDMXN";
  private static final BigDecimal REPORTO_FACE_VALUE_PER_TITULO = BigDecimal.valueOf(100);
  private static final int MAX_ASSET_SYMBOL_LENGTH = 10;

  private final GbmStatementParserPort statementParser;
  private final TransactionCommandUseCase transactionCommandUseCase;

  @Override
  public StatementImportResult importStatement(UUID userId, UploadedDocument document) {
    GbmStatementParseResult parseResult = statementParser.parse(document.content());

    int accepted = 0;
    int duplicate = 0;
    int skipped = 0;
    int rejected = 0;
    List<String> messages = new ArrayList<>();

    for (ParsedStatementRow row : parseResult.rows()) {
      RowOutcome outcome = processRow(userId, parseResult.contractNumber(), row);
      switch (outcome.category()) {
        case ACCEPTED -> accepted++;
        case DUPLICATE -> duplicate++;
        case SKIPPED -> skipped++;
        case REJECTED -> rejected++;
      }
      if (outcome.message() != null) {
        messages.add(outcome.message());
      }
    }

    return new StatementImportResult(
        document.fileName(), accepted, duplicate, skipped, rejected, messages);
  }

  private RowOutcome processRow(UUID userId, String contractNumber, ParsedStatementRow row) {
    return switch (row.kind()) {
      case NOISE -> new RowOutcome(
          Category.SKIPPED, "Omitido (%s) folio=%s".formatted(row.description(), row.folio()));
      case REPORTO_SNAPSHOT -> registerReportoSnapshot(userId, contractNumber, row);
      case FOREX_BUY, FOREX_SELL -> registerForex(userId, contractNumber, row);
      case EQUITY_BUY, EQUITY_SELL -> registerEquity(userId, contractNumber, row);
    };
  }

  private RowOutcome registerReportoSnapshot(
      UUID userId, String contractNumber, ParsedStatementRow row) {
    String idempotencyKey =
        StatementIdempotencyKeys.sha256(
            "gbm-statement-reporto", contractNumber, row.emisora(), row.operationDate().toString());
    String assetSymbol = truncateSymbol(row.emisora().replace(" ", ""));

    BuyTransactionRequest request =
        new BuyTransactionRequest(
            assetSymbol,
            "GOVERNMENT_BOND",
            row.quantity(),
            row.pricePerUnit(),
            BigDecimal.ZERO,
            toTransactionDate(row.operationDate()),
            "import:gbm-statement:reporto:" + row.emisora(),
            "GBM Reporto " + row.emisora(),
            EXCHANGE_LOCAL,
            BROKER,
            CURRENCY_MXN,
            row.quantity().multiply(REPORTO_FACE_VALUE_PER_TITULO),
            row.maturityDate(),
            row.couponRate(),
            Boolean.FALSE);

    return registerBuy(userId, idempotencyKey, request, row.emisora());
  }

  private RowOutcome registerForex(UUID userId, String contractNumber, ParsedStatementRow row) {
    String idempotencyKey =
        StatementIdempotencyKeys.sha256(
            "gbm-statement-movement", contractNumber, String.valueOf(row.folio()));

    if (row.kind() == ParsedStatementRow.RowKind.FOREX_BUY) {
      BuyTransactionRequest request =
          new BuyTransactionRequest(
              FOREX_SYMBOL,
              "FOREX",
              row.quantity(),
              row.pricePerUnit(),
              row.commission(),
              toTransactionDate(row.operationDate()),
              "import:gbm-statement:folio:" + row.folio(),
              "Dolar estadounidense",
              null,
              BROKER,
              CURRENCY_MXN,
              null,
              null,
              null,
              Boolean.FALSE);
      return registerBuy(userId, idempotencyKey, request, FOREX_SYMBOL);
    }

    SellTransactionRequest request =
        new SellTransactionRequest(
            FOREX_SYMBOL,
            "FOREX",
            row.quantity(),
            row.pricePerUnit(),
            row.commission(),
            toTransactionDate(row.operationDate()),
            "import:gbm-statement:folio:" + row.folio(),
            "Dolar estadounidense",
            null,
            BROKER,
            CURRENCY_MXN,
            null,
            null,
            null,
            Boolean.FALSE);
    return registerSell(userId, idempotencyKey, request, FOREX_SYMBOL);
  }

  private RowOutcome registerEquity(UUID userId, String contractNumber, ParsedStatementRow row) {
    String idempotencyKey =
        StatementIdempotencyKeys.sha256(
            "gbm-statement-movement", contractNumber, String.valueOf(row.folio()));
    String assetSymbol = truncateSymbol(row.emisora());

    if (row.kind() == ParsedStatementRow.RowKind.EQUITY_BUY) {
      BuyTransactionRequest request =
          new BuyTransactionRequest(
              assetSymbol,
              "STOCK",
              row.quantity(),
              row.pricePerUnit(),
              row.commission(),
              toTransactionDate(row.operationDate()),
              "import:gbm-statement:folio:" + row.folio(),
              null,
              EXCHANGE_LOCAL,
              BROKER,
              CURRENCY_MXN,
              null,
              null,
              null,
              Boolean.FALSE);
      return registerBuy(userId, idempotencyKey, request, assetSymbol);
    }

    SellTransactionRequest request =
        new SellTransactionRequest(
            assetSymbol,
            "STOCK",
            row.quantity(),
            row.pricePerUnit(),
            row.commission(),
            toTransactionDate(row.operationDate()),
            "import:gbm-statement:folio:" + row.folio(),
            null,
            EXCHANGE_LOCAL,
            BROKER,
            CURRENCY_MXN,
            null,
            null,
            null,
            Boolean.FALSE);
    return registerSell(userId, idempotencyKey, request, assetSymbol);
  }

  private RowOutcome registerBuy(
      UUID userId, String idempotencyKey, BuyTransactionRequest request, String assetSymbol) {
    OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
    try {
      TransactionResponse response =
          transactionCommandUseCase.registerBuyTransaction(userId, request, idempotencyKey);
      return classifyOutcome(response, before, assetSymbol);
    } catch (RuntimeException ex) {
      log.warn("Fallo al registrar transaccion de estado de cuenta GBM asset={}", assetSymbol, ex);
      return new RowOutcome(
          Category.REJECTED, "Error al registrar " + assetSymbol + ": " + ex.getMessage());
    }
  }

  private RowOutcome registerSell(
      UUID userId, String idempotencyKey, SellTransactionRequest request, String assetSymbol) {
    OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
    try {
      TransactionResponse response =
          transactionCommandUseCase.registerSellTransaction(userId, request, idempotencyKey);
      return classifyOutcome(response, before, assetSymbol);
    } catch (RuntimeException ex) {
      log.warn("Fallo al registrar transaccion de estado de cuenta GBM asset={}", assetSymbol, ex);
      return new RowOutcome(
          Category.REJECTED, "Error al registrar " + assetSymbol + ": " + ex.getMessage());
    }
  }

  private RowOutcome classifyOutcome(
      TransactionResponse response, OffsetDateTime before, String assetSymbol) {
    boolean isNew = !response.createdAt().isBefore(before.minusSeconds(2));
    return isNew
        ? new RowOutcome(Category.ACCEPTED, null)
        : new RowOutcome(Category.DUPLICATE, "Ya existente (omitido): " + assetSymbol);
  }

  private OffsetDateTime toTransactionDate(java.time.LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
  }

  private String truncateSymbol(String symbol) {
    String normalized = symbol.trim().toUpperCase(java.util.Locale.ROOT);
    return normalized.length() > MAX_ASSET_SYMBOL_LENGTH
        ? normalized.substring(0, MAX_ASSET_SYMBOL_LENGTH)
        : normalized;
  }

  private enum Category {
    ACCEPTED,
    DUPLICATE,
    SKIPPED,
    REJECTED
  }

  private record RowOutcome(Category category, String message) {}
}
