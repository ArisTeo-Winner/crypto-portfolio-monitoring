package com.mx.cryptomonitor.statementimport.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportDriveWealthConfirmationsUseCase;
import com.mx.cryptomonitor.statementimport.application.port.out.DriveWealthConfirmationParserPort;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedConfirmationRow;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;
import com.mx.cryptomonitor.transaction.application.dto.request.ImportedBrokerKind;
import com.mx.cryptomonitor.transaction.application.dto.request.ImportedStockTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriveWealthImportService implements ImportDriveWealthConfirmationsUseCase {

  private static final String BROKER = "DriveWealth";
  private static final String EXCHANGE_GLOBAL = "SIC";
  private static final String CURRENCY_USD = "USD";

  private final DriveWealthConfirmationParserPort confirmationParser;
  private final TransactionCommandUseCase transactionCommandUseCase;

  @Override
  public List<StatementImportResult> importConfirmations(
      UUID userId, List<UploadedDocument> documents) {
    return documents.stream().map(document -> importOne(userId, document)).toList();
  }

  private StatementImportResult importOne(UUID userId, UploadedDocument document) {
    ParsedConfirmationRow row;
    try {
      row = confirmationParser.parse(document.content());
    } catch (UnrecognizedBrokerDocumentException | InvalidStatementDocumentException ex) {
      return new StatementImportResult(
          document.fileName(), 0, 0, 0, 1, List.of("Documento invalido: " + ex.getMessage()));
    }

    String idempotencyKey =
        StatementIdempotencyKeys.sha256(
            "drivewealth-confirmation",
            row.tradeDate().toString(),
            row.symbol(),
            row.quantity().toPlainString(),
            row.netAmount().toPlainString());

    boolean isBuy = "Buy".equalsIgnoreCase(row.action());
    OffsetDateTime transactionDate =
        row.tradeDate().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
    String assetSymbol = row.symbol().toUpperCase(Locale.ROOT);

    try {
      ImportedStockTransactionRequest request =
          new ImportedStockTransactionRequest(
              assetSymbol,
              row.securityName(),
              isBuy,
              row.quantity(),
              row.price(),
              transactionDate,
              null,
              EXCHANGE_GLOBAL,
              BROKER,
              CURRENCY_USD,
              row.principalAmount(),
              row.commission(),
              row.transactionFee(),
              row.otherFees(),
              row.netAmount(),
              ImportedBrokerKind.DRIVEWEALTH);
      TransactionResponse response =
          transactionCommandUseCase.registerImportedStockTransaction(
              userId, request, idempotencyKey);

      boolean isNew = !response.createdAt().isBefore(before.minusSeconds(2));
      return isNew
          ? new StatementImportResult(document.fileName(), 1, 0, 0, 0, List.of())
          : new StatementImportResult(
              document.fileName(), 0, 1, 0, 0, List.of("Ya existente (omitido): " + assetSymbol));
    } catch (RuntimeException ex) {
      log.warn("Fallo al registrar confirmacion DriveWealth symbol={}", assetSymbol, ex);
      return new StatementImportResult(
          document.fileName(), 0, 0, 0, 1, List.of("Error al registrar: " + ex.getMessage()));
    }
  }
}
