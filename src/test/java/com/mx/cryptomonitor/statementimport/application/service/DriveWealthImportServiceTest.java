package com.mx.cryptomonitor.statementimport.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.application.port.out.DriveWealthConfirmationParserPort;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedConfirmationRow;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;
import com.mx.cryptomonitor.transaction.application.dto.request.ImportedStockTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;

class DriveWealthImportServiceTest {

  private final DriveWealthConfirmationParserPort parser =
      mock(DriveWealthConfirmationParserPort.class);
  private final TransactionCommandUseCase commandUseCase = mock(TransactionCommandUseCase.class);
  private final DriveWealthImportService service =
      new DriveWealthImportService(parser, commandUseCase);

  @Test
  void mapsCrclConfirmationToImportedRequestWithFullFrictionInputs() {
    ParsedConfirmationRow row =
        new ParsedConfirmationRow(
            "CRCL",
            "CIRCLE STAR ENERGY CORP COM",
            "Buy",
            new BigDecimal("0.95368698"),
            new BigDecimal("108.7988"),
            LocalDate.of(2025, 6, 11),
            new BigDecimal("103.76"),
            new BigDecimal("0.25"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("104.01"));
    when(parser.parse(any())).thenReturn(row);

    TransactionResponse response = mock(TransactionResponse.class);
    when(response.createdAt()).thenReturn(OffsetDateTime.now());
    when(response.transactionId()).thenReturn(UUID.randomUUID());
    when(commandUseCase.registerImportedStockTransaction(any(), any(), any())).thenReturn(response);

    UUID userId = UUID.randomUUID();
    List<StatementImportResult> results =
        service.importConfirmations(
            userId, List.of(new UploadedDocument("crcl.pdf", new byte[] {1})));

    assertThat(results).hasSize(1);

    ArgumentCaptor<ImportedStockTransactionRequest> captor =
        ArgumentCaptor.forClass(ImportedStockTransactionRequest.class);
    verify(commandUseCase).registerImportedStockTransaction(eq(userId), captor.capture(), any());
    ImportedStockTransactionRequest request = captor.getValue();

    assertThat(request.assetSymbol()).isEqualTo("CRCL");
    assertThat(request.buy()).isTrue();
    assertThat(request.quantity()).isEqualByComparingTo("0.95368698");
    assertThat(request.pricePerUnit()).isEqualByComparingTo("108.7988");
    assertThat(request.principalAmount()).isEqualByComparingTo("103.76");
    assertThat(request.commission()).isEqualByComparingTo("0.25");
    assertThat(request.transactionFee()).isEqualByComparingTo("0.00");
    assertThat(request.otherFees()).isEqualByComparingTo("0.00");
    assertThat(request.netAmount()).isEqualByComparingTo("104.01");
    assertThat(request.broker()).isEqualTo("DriveWealth");
    assertThat(request.currency()).isEqualTo("USD");
    assertThat(request.exchange()).isEqualTo("SIC");
    assertThat(request.assetName()).isEqualTo("CIRCLE STAR ENERGY CORP COM");
    assertThat(request.brokerKind())
        .isEqualTo(
            com.mx.cryptomonitor.transaction.application.dto.request.ImportedBrokerKind
                .DRIVEWEALTH);
  }
}
