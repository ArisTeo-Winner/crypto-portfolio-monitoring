package com.mx.cryptomonitor.statementimport.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportGbmEquityConfirmationsUseCase;
import com.mx.cryptomonitor.statementimport.application.port.out.GbmEquityConfirmationParserPort;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedGbmEquityConfirmationRow;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;
import com.mx.cryptomonitor.transaction.application.dto.request.ImportedBrokerKind;
import com.mx.cryptomonitor.transaction.application.dto.request.ImportedStockTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Importa comprobantes de renta variable de GBM casa de bolsa (MXN). Opcion 2 del wiring de
 * friccion: el modulo transaction recalcula comision (0.25%) e IVA (16%) desde qty*precio y valida
 * contra el total reportado (netAmount), por eso aqui no se envian comision/IVA crudos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbmEquityImportService implements ImportGbmEquityConfirmationsUseCase {

  private static final String BROKER = "GBM";
  private static final String EXCHANGE_BMV = "BMV";
  private static final String CURRENCY_MXN = "MXN";

  private final GbmEquityConfirmationParserPort confirmationParser;
  private final TransactionCommandUseCase transactionCommandUseCase;

  @Override
  public List<StatementImportResult> importConfirmations(
      UUID userId, List<UploadedDocument> documents) {
    return documents.stream().map(document -> importOne(userId, document)).toList();
  }

  private StatementImportResult importOne(UUID userId, UploadedDocument document) {
    ParsedGbmEquityConfirmationRow row;
    try {
      row = confirmationParser.parse(document.content());
    } catch (UnrecognizedBrokerDocumentException | InvalidStatementDocumentException ex) {
      return new StatementImportResult(
          document.fileName(), 0, 0, 0, 1, List.of("Documento invalido: " + ex.getMessage()));
    }

    if (row.tradeDate() == null) {
      return new StatementImportResult(
          document.fileName(), 0, 0, 0, 1, List.of("Sin fecha de operacion en el comprobante GBM"));
    }

    String assetSymbol = row.symbol().toUpperCase(Locale.ROOT);
    boolean isBuy = "Compra".equalsIgnoreCase(row.action());
    OffsetDateTime transactionDate =
        row.tradeDate().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

    String idempotencyKey =
        StatementIdempotencyKeys.sha256(
            "gbm-equity-confirmation",
            row.tradeDate().toString(),
            assetSymbol,
            row.quantity().toPlainString(),
            row.totalAmount().toPlainString());

    try {
      ImportedStockTransactionRequest request =
          new ImportedStockTransactionRequest(
              assetSymbol,
              row.symbol(),
              isBuy,
              row.quantity(),
              row.unitPrice(),
              transactionDate,
              null,
              EXCHANGE_BMV,
              BROKER,
              CURRENCY_MXN,
              null,
              null,
              null,
              null,
              row.totalAmount(),
              ImportedBrokerKind.GBM_MX_EQUITY);
      TransactionResponse response =
          transactionCommandUseCase.registerImportedStockTransaction(
              userId, request, idempotencyKey);

      boolean isNew = !response.createdAt().isBefore(before.minusSeconds(2));
      return isNew
          ? new StatementImportResult(document.fileName(), 1, 0, 0, 0, List.of())
          : new StatementImportResult(
              document.fileName(), 0, 1, 0, 0, List.of("Ya existente (omitido): " + assetSymbol));
    } catch (RuntimeException ex) {
      log.warn("Fallo al registrar comprobante GBM symbol={}", assetSymbol, ex);
      return new StatementImportResult(
          document.fileName(), 0, 0, 0, 1, List.of("Error al registrar: " + ex.getMessage()));
    }
  }
}
