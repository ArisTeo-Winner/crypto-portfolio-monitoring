package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.statementimport.application.port.out.DriveWealthConfirmationParserPort;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedConfirmationRow;

import lombok.RequiredArgsConstructor;

/**
 * Parsea confirmaciones de operacion DriveWealth/GBMP (una operacion por archivo). Ver plan de
 * ingesta de estados de cuenta GBM para el detalle de columnas reales observadas.
 */
@Component
@RequiredArgsConstructor
public class DriveWealthConfirmationParser implements DriveWealthConfirmationParserPort {

  private static final Pattern DATA_ROW_PATTERN =
      Pattern.compile(
          "^(\\S+)\\s+(.+?)\\s+([A-Z])\\s+(Buy|Sell)\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\s+"
              + "(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d{1,2}/\\d{1,2}/\\d{4})\\s+"
              + "\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\S+\\s*$",
          Pattern.MULTILINE);
  private static final Pattern PRINCIPAL_PATTERN =
      Pattern.compile("Principal Amount\\s+\\$(-?[\\d,]+\\.\\d+)");
  private static final Pattern COMMISSION_PATTERN =
      Pattern.compile("Commission\\s+\\$(-?[\\d,]+\\.\\d+)");
  private static final Pattern TRANSACTION_FEE_PATTERN =
      Pattern.compile("Transaction Fee\\s+\\$(-?[\\d,]+\\.\\d+)");
  private static final Pattern OTHER_FEES_PATTERN =
      Pattern.compile("Other Fees\\s*/\\s*Credits\\s+\\$(-?[\\d,]+\\.\\d+)");
  private static final Pattern NET_AMOUNT_PATTERN =
      Pattern.compile("Net Amount\\s+\\$(-?[\\d,]+\\.\\d+)");
  private static final DateTimeFormatter TRADE_DATE_FORMAT =
      DateTimeFormatter.ofPattern("M/d/yyyy");

  private final PdfBoxTextExtractor textExtractor;

  @Override
  public ParsedConfirmationRow parse(byte[] pdfContent) {
    List<String> pages = textExtractor.extractPagesText(pdfContent);
    return parseText(String.join("\n", pages).replace("\r", ""));
  }

  ParsedConfirmationRow parseText(String fullText) {
    validateEntity(fullText);

    Matcher rowMatcher = DATA_ROW_PATTERN.matcher(fullText);
    if (!rowMatcher.find()) {
      throw new InvalidStatementDocumentException(
          "No se encontro la fila de operacion en la confirmacion DriveWealth");
    }

    String symbol = rowMatcher.group(1);
    String securityName = rowMatcher.group(2).trim();
    String action = rowMatcher.group(4);
    BigDecimal quantity = parseDecimal(rowMatcher.group(5));
    BigDecimal price = parseDecimal(rowMatcher.group(6));
    LocalDate tradeDate = LocalDate.parse(rowMatcher.group(7), TRADE_DATE_FORMAT);

    BigDecimal commission = findAmount(fullText, COMMISSION_PATTERN).orElse(BigDecimal.ZERO);
    BigDecimal transactionFee =
        findAmount(fullText, TRANSACTION_FEE_PATTERN).orElse(BigDecimal.ZERO);
    BigDecimal otherFees = findAmount(fullText, OTHER_FEES_PATTERN).orElse(BigDecimal.ZERO);
    BigDecimal netAmount =
        findAmount(fullText, NET_AMOUNT_PATTERN)
            .orElseThrow(
                () ->
                    new InvalidStatementDocumentException(
                        "No se encontro el Net Amount en la confirmacion DriveWealth"));
    BigDecimal principalAmount =
        findAmount(fullText, PRINCIPAL_PATTERN).orElseGet(() -> quantity.multiply(price));

    return new ParsedConfirmationRow(
        symbol,
        securityName,
        action,
        quantity,
        price,
        tradeDate,
        principalAmount,
        commission,
        transactionFee,
        otherFees,
        netAmount);
  }

  private void validateEntity(String fullText) {
    boolean isDriveWealth = fullText.contains("DriveWealth, LLC") && fullText.contains("FINRA");
    boolean isGbmp = fullText.contains("Account Number:GBMP-");
    if (!isDriveWealth || !isGbmp) {
      throw new UnrecognizedBrokerDocumentException(
          "El documento no corresponde a una confirmacion DriveWealth/GBMP reconocida");
    }
  }

  private Optional<BigDecimal> findAmount(String text, Pattern pattern) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.of(parseDecimal(matcher.group(1))) : Optional.empty();
  }

  private BigDecimal parseDecimal(String raw) {
    return new BigDecimal(raw.replace(",", "").trim());
  }
}
