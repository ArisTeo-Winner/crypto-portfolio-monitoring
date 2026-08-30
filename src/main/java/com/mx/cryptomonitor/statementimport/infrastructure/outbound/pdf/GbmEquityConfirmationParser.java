package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.statementimport.application.port.out.GbmEquityConfirmationParserPort;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedGbmEquityConfirmationRow;

import lombok.RequiredArgsConstructor;

/**
 * Parsea el comprobante de operacion de renta variable de GBM casa de bolsa (MXN): una operacion
 * por comprobante, con comision (0.25%) e IVA (16% sobre la comision).
 *
 * <p>ANDAMIAJE: los patrones estan calibrados contra un ejemplo sintetico basado en la captura del
 * ticket FMTY 14 (no contra un PDF real todavia). Cuando llegue el comprobante/estado real de GBM
 * hay que revisar el layout (orden de lineas, etiquetas con acentos, formato de fecha en espanol) y
 * ajustar los regex.
 */
@Component
@RequiredArgsConstructor
public class GbmEquityConfirmationParser implements GbmEquityConfirmationParserPort {

  private static final Pattern EMISORA_PATTERN = Pattern.compile("(?m)^Emisora\\s+(.+?)\\s*$");
  private static final Pattern ACTION_PATTERN =
      Pattern.compile("Tipo de operaci[oó]n\\s+(Compra|Venta)");
  private static final Pattern QUANTITY_PATTERN =
      Pattern.compile("(?m)^T[ií]tulos\\s+([\\d,]+)\\s*$");
  private static final Pattern PRICE_PATTERN =
      Pattern.compile("Precio por t[ií]tulo\\s+\\$?([\\d,]+\\.\\d+)");
  private static final Pattern COMMISSION_PATTERN =
      Pattern.compile("Comisi[oó]n\\s+[\\d.]+%\\s+\\$?([\\d,]+\\.\\d+)");
  private static final Pattern IVA_PATTERN =
      Pattern.compile("IVA\\s+\\d+%\\s+\\$?([\\d,]+\\.\\d+)");
  private static final Pattern TOTAL_PATTERN =
      Pattern.compile("Orden completada\\s+\\$?([\\d,]+\\.\\d+)");
  private static final Pattern CONTRATO_PATTERN = Pattern.compile("Contrato\\s+(\\S+)");
  private static final Pattern FOLIO_PATTERN = Pattern.compile("Folio\\s+(\\S+)");
  private static final Pattern DATE_PATTERN =
      Pattern.compile("(\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4})");
  private static final DateTimeFormatter TRADE_DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final PdfBoxTextExtractor textExtractor;

  @Override
  public ParsedGbmEquityConfirmationRow parse(byte[] pdfContent) {
    List<String> pages = textExtractor.extractPagesText(pdfContent);
    return parseText(String.join("\n", pages).replace("\r", ""));
  }

  ParsedGbmEquityConfirmationRow parseText(String fullText) {
    validateEntity(fullText);

    String symbol =
        findText(fullText, EMISORA_PATTERN)
            .orElseThrow(
                () ->
                    new InvalidStatementDocumentException(
                        "No se encontro la Emisora en el comprobante GBM"));
    String action = findText(fullText, ACTION_PATTERN).orElse("Compra");
    BigDecimal quantity =
        findAmount(fullText, QUANTITY_PATTERN)
            .orElseThrow(
                () ->
                    new InvalidStatementDocumentException(
                        "No se encontraron los Titulos en el comprobante GBM"));
    BigDecimal unitPrice =
        findAmount(fullText, PRICE_PATTERN)
            .orElseThrow(
                () ->
                    new InvalidStatementDocumentException(
                        "No se encontro el Precio por titulo en el comprobante GBM"));
    BigDecimal commission = findAmount(fullText, COMMISSION_PATTERN).orElse(BigDecimal.ZERO);
    BigDecimal iva = findAmount(fullText, IVA_PATTERN).orElse(BigDecimal.ZERO);
    BigDecimal totalAmount =
        findAmount(fullText, TOTAL_PATTERN)
            .orElseThrow(
                () ->
                    new InvalidStatementDocumentException(
                        "No se encontro el monto total en el comprobante GBM"));
    String contrato = findText(fullText, CONTRATO_PATTERN).orElse(null);
    String folio = findText(fullText, FOLIO_PATTERN).orElse(null);
    LocalDate tradeDate =
        findText(fullText, DATE_PATTERN)
            .map(raw -> LocalDate.parse(raw, TRADE_DATE_FORMAT))
            .orElse(null);

    return new ParsedGbmEquityConfirmationRow(
        symbol,
        action,
        quantity,
        unitPrice,
        commission,
        iva,
        totalAmount,
        contrato,
        folio,
        tradeDate);
  }

  private void validateEntity(String fullText) {
    boolean looksLikeGbmEquity =
        fullText.contains("GBM") && fullText.contains("Emisora") && fullText.contains("MXN");
    if (!looksLikeGbmEquity) {
      throw new UnrecognizedBrokerDocumentException(
          "El documento no corresponde a un comprobante de renta variable GBM reconocido");
    }
  }

  private Optional<String> findText(String text, Pattern pattern) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
  }

  private Optional<BigDecimal> findAmount(String text, Pattern pattern) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.of(parseDecimal(matcher.group(1))) : Optional.empty();
  }

  private BigDecimal parseDecimal(String raw) {
    return new BigDecimal(raw.replace(",", "").trim());
  }
}
