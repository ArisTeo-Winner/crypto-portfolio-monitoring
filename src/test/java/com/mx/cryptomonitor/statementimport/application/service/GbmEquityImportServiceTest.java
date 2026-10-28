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
import com.mx.cryptomonitor.statementimport.application.port.out.GbmEquityConfirmationParserPort;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedGbmEquityConfirmationRow;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;
import com.mx.cryptomonitor.transaction.application.dto.request.ImportedBrokerKind;
import com.mx.cryptomonitor.transaction.application.dto.request.ImportedStockTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;

class GbmEquityImportServiceTest {

  private final GbmEquityConfirmationParserPort parser =
      mock(GbmEquityConfirmationParserPort.class);
  private final TransactionCommandUseCase commandUseCase = mock(TransactionCommandUseCase.class);
  private final GbmEquityImportService service = new GbmEquityImportService(parser, commandUseCase);

  @Test
  void mapsFmtyTicketToImportedRequestForMxEquityFrictionPath() {
    ParsedGbmEquityConfirmationRow row =
        new ParsedGbmEquityConfirmationRow(
            "FMTY 14",
            "Compra",
            new BigDecimal("100"),
            new BigDecimal("15.50"),
            new BigDecimal("3.87"),
            new BigDecimal("0.620"),
            new BigDecimal("1554.49"),
            "CI43HI02",
            "102580702",
            LocalDate.of(2026, 1, 2));
    when(parser.parse(any())).thenReturn(row);

    TransactionResponse response = mock(TransactionResponse.class);
    when(response.createdAt()).thenReturn(OffsetDateTime.now());
    when(response.transactionId()).thenReturn(UUID.randomUUID());
    when(commandUseCase.registerImportedStockTransaction(any(), any(), any())).thenReturn(response);

    UUID userId = UUID.randomUUID();
    List<StatementImportResult> results =
        service.importConfirmations(
            userId, List.of(new UploadedDocument("fmty.pdf", new byte[] {1})));

    assertThat(results).hasSize(1);

    ArgumentCaptor<ImportedStockTransactionRequest> captor =
        ArgumentCaptor.forClass(ImportedStockTransactionRequest.class);
    verify(commandUseCase).registerImportedStockTransaction(eq(userId), captor.capture(), any());
    ImportedStockTransactionRequest request = captor.getValue();

    assertThat(request.brokerKind()).isEqualTo(ImportedBrokerKind.GBM_MX_EQUITY);
    assertThat(request.assetSymbol()).isEqualTo("FMTY 14");
    assertThat(request.buy()).isTrue();
    assertThat(request.quantity()).isEqualByComparingTo("100");
    assertThat(request.pricePerUnit()).isEqualByComparingTo("15.50");
    assertThat(request.netAmount()).isEqualByComparingTo("1554.49");
    assertThat(request.broker()).isEqualTo("GBM");
    assertThat(request.currency()).isEqualTo("MXN");
    assertThat(request.exchange()).isEqualTo("BMV");
    // Opcion 2: comision/IVA/principal se recalculan dentro de transaction, no se envian crudos.
    assertThat(request.commission()).isNull();
    assertThat(request.principalAmount()).isNull();
  }
}
