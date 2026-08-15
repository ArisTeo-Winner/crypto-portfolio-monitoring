package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.statementimport.application.port.out.GbmStatementParserPort;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.GbmStatementParseResult;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedStatementRow;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedStatementRow.RowKind;

import lombok.RequiredArgsConstructor;

/**
 * Parsea estados de cuenta mensuales GBM. Nunca procesa el texto de la pagina "CONCEPTOS DEL
 * CFDI"/"DATOS FISCALES": esa pagina se descarta antes de extraer nada de ella, para no tocar datos
 * de sello fiscal del SAT que no aportan al parser.
 */
@Component
@RequiredArgsConstructor
public class GbmStatementParser implements GbmStatementParserPort {

  private static final Pattern CONTRACT_PATTERN = Pattern.compile("Contrato:\\s*(\\S+)");
  private static final Pattern PERIOD_PATTERN =
      Pattern.compile(
          "DEL\\s+\\d{1,2}\\s+AL\\s+\\d{1,2}\\s+DE\\s+([A-ZÑ]+)\\s+DE\\s+(\\d{4})",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern REPORTO_ROW_PREFIX =
      Pattern.compile("^\\s*([A-Z]{1,10})\\s+(\\d{6})\\s+(.+)$");
  private static final Pattern MOVEMENT_ROW_PREFIX =
      Pattern.compile("^\\s*(\\d{2})/\\d{2}\\s+(\\S+)\\s+(.+)$");
  private static final Pattern INTEGER_TOKEN = Pattern.compile("^-?\\d+$");

  private static final List<String> KNOWN_DESCRIPTIONS =
      List.of(
          "Efectivo Inicial del Mes",
          "DEPOSITO DE EFECTIVO",
          "RETIRO DE EFECTIVO",
          "Compra en Reporto",
          "Venta en Reporto",
          "Vencimiento de Reporto",
          "Compra",
          "Venta");

  private static final Map<String, Integer> SPANISH_MONTHS =
      Map.ofEntries(
          Map.entry("ENERO", 1),
          Map.entry("FEBRERO", 2),
          Map.entry("MARZO", 3),
          Map.entry("ABRIL", 4),
          Map.entry("MAYO", 5),
          Map.entry("JUNIO", 6),
          Map.entry("JULIO", 7),
          Map.entry("AGOSTO", 8),
          Map.entry("SEPTIEMBRE", 9),
          Map.entry("OCTUBRE", 10),
          Map.entry("NOVIEMBRE", 11),
          Map.entry("DICIEMBRE", 12));

  private final PdfBoxTextExtractor textExtractor;

  @Override
  public GbmStatementParseResult parse(byte[] pdfContent) {
    List<String> pages = textExtractor.extractPagesText(pdfContent);
    String text =
        String.join("\n", pages.stream().filter(page -> !isFiscalDocumentPage(page)).toList())
            .replace("\r", "");

    String contractNumber = validateEntityAndExtractContract(text);
    LocalDate periodEnd = parsePeriodEnd(text);

    List<ParsedStatementRow> rows = new ArrayList<>();
    rows.addAll(parseReportoSnapshot(text, periodEnd));
    rows.addAll(parseMovements(text, periodEnd));
    return new GbmStatementParseResult(contractNumber, rows);
  }

  private boolean isFiscalDocumentPage(String pageText) {
    return pageText.contains("CONCEPTOS DEL CFDI") || pageText.contains("DATOS FISCALES");
  }

  private String validateEntityAndExtractContract(String text) {
    boolean isGbmCasaDeBolsa = text.toUpperCase(Locale.ROOT).contains("CASA DE BOLSA");
    Matcher contractMatcher = CONTRACT_PATTERN.matcher(text);
    if (!isGbmCasaDeBolsa || !contractMatcher.find() || contractMatcher.group(1).isBlank()) {
      throw new UnrecognizedBrokerDocumentException(
          "El documento no corresponde a un estado de cuenta GBM reconocido");
    }
    return contractMatcher.group(1);
  }

  private LocalDate parsePeriodEnd(String text) {
    Matcher matcher = PERIOD_PATTERN.matcher(text);
    if (!matcher.find()) {
      throw new InvalidStatementDocumentException(
          "No se pudo determinar el periodo del estado de cuenta");
    }
    String monthName = matcher.group(1).toUpperCase(Locale.ROOT);
    Integer month = SPANISH_MONTHS.get(monthName);
    if (month == null) {
      throw new InvalidStatementDocumentException("Mes de periodo no reconocido: " + monthName);
    }
    int year = Integer.parseInt(matcher.group(2));
    return LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
  }

  private List<ParsedStatementRow> parseReportoSnapshot(String text, LocalDate periodEnd) {
    List<ParsedStatementRow> rows = new ArrayList<>();
    int headerIdx = text.indexOf("DEUDA EN REPORTO");
    if (headerIdx < 0) {
      return rows;
    }
    int totalIdx = text.indexOf("Total: DEUDA EN REPORTO", headerIdx + 1);
    String section = totalIdx > 0 ? text.substring(headerIdx, totalIdx) : text.substring(headerIdx);

    for (String line : section.split("\n")) {
      Matcher matcher = REPORTO_ROW_PREFIX.matcher(line);
      if (!matcher.matches()) {
        continue;
      }
      String ticker = matcher.group(1);
      String maturityYyMmDd = matcher.group(2);
      String[] tokens = matcher.group(3).trim().split("\\s+");
      if (tokens.length < 10) {
        continue;
      }
      BigDecimal quantity = parseDecimal(tokens[1]);
      BigDecimal couponRate = parseDecimal(tokens[2]);
      BigDecimal pricePerUnit = parseDecimal(tokens[5]);
      LocalDate maturityDate = parseYyMmDd(maturityYyMmDd);

      rows.add(
          new ParsedStatementRow(
              RowKind.REPORTO_SNAPSHOT,
              null,
              periodEnd,
              "Deuda en Reporto",
              ticker + " " + maturityYyMmDd,
              quantity,
              pricePerUnit,
              BigDecimal.ZERO,
              couponRate,
              maturityDate));
    }
    return rows;
  }

  private List<ParsedStatementRow> parseMovements(String text, LocalDate periodEnd) {
    List<ParsedStatementRow> rows = new ArrayList<>();
    int startIdx = text.indexOf("DESGLOSE DE MOVIMIENTOS");
    if (startIdx < 0) {
      return rows;
    }
    int endIdx = text.indexOf("RENDIMIENTO DEL PORTAFOLIO", startIdx);
    String section = endIdx > 0 ? text.substring(startIdx, endIdx) : text.substring(startIdx);

    for (String line : section.split("\n")) {
      Matcher matcher = MOVEMENT_ROW_PREFIX.matcher(line);
      if (!matcher.matches()) {
        continue;
      }
      int day = Integer.parseInt(matcher.group(1));
      String folio = matcher.group(2);
      String rest = matcher.group(3).trim();

      String matchedDescription = findKnownDescription(rest);
      if (matchedDescription == null) {
        continue;
      }
      String remainder = rest.substring(matchedDescription.length()).trim();
      LocalDate operationDate = safeDayOfMonth(periodEnd, day);

      rows.add(buildMovementRow(matchedDescription, remainder, folio, operationDate));
    }
    return rows;
  }

  private ParsedStatementRow buildMovementRow(
      String description, String remainder, String folio, LocalDate operationDate) {
    if (description.equalsIgnoreCase("Efectivo Inicial del Mes")
        || description.toUpperCase(Locale.ROOT).contains("REPORTO")
        || description.equalsIgnoreCase("DEPOSITO DE EFECTIVO")
        || description.equalsIgnoreCase("RETIRO DE EFECTIVO")) {
      return new ParsedStatementRow(
          RowKind.NOISE, folio, operationDate, description, null, null, null, null, null, null);
    }

    boolean isBuy = description.equalsIgnoreCase("Compra");
    String[] tokens = remainder.split("\\s+");
    int qtyIndex = -1;
    for (int i = 0; i < tokens.length; i++) {
      if (INTEGER_TOKEN.matcher(tokens[i]).matches()) {
        qtyIndex = i;
        break;
      }
    }
    if (qtyIndex <= 0 || qtyIndex + 2 >= tokens.length) {
      return new ParsedStatementRow(
          RowKind.NOISE, folio, operationDate, description, null, null, null, null, null, null);
    }

    String emisora = String.join(" ", java.util.Arrays.copyOfRange(tokens, 0, qtyIndex));
    BigDecimal quantity = parseDecimal(tokens[qtyIndex]);
    BigDecimal pricePerUnit = parseDecimal(tokens[qtyIndex + 1]);
    BigDecimal commission = parseDecimal(tokens[qtyIndex + 2]);

    boolean isForex = emisora.equalsIgnoreCase("DOLARES");
    RowKind kind;
    if (isForex) {
      kind = isBuy ? RowKind.FOREX_BUY : RowKind.FOREX_SELL;
    } else {
      kind = isBuy ? RowKind.EQUITY_BUY : RowKind.EQUITY_SELL;
    }

    return new ParsedStatementRow(
        kind,
        folio,
        operationDate,
        description,
        emisora,
        quantity,
        pricePerUnit,
        commission,
        null,
        null);
  }

  private String findKnownDescription(String rest) {
    for (String candidate : KNOWN_DESCRIPTIONS) {
      if (rest.regionMatches(true, 0, candidate, 0, candidate.length())) {
        return candidate;
      }
    }
    return null;
  }

  private LocalDate safeDayOfMonth(LocalDate periodEnd, int day) {
    int lastDayOfMonth = periodEnd.lengthOfMonth();
    int safeDay = Math.min(Math.max(day, 1), lastDayOfMonth);
    return periodEnd.withDayOfMonth(safeDay);
  }

  private LocalDate parseYyMmDd(String yyMmDd) {
    int yy = Integer.parseInt(yyMmDd.substring(0, 2));
    int mm = Integer.parseInt(yyMmDd.substring(2, 4));
    int dd = Integer.parseInt(yyMmDd.substring(4, 6));
    return LocalDate.of(2000 + yy, mm, dd);
  }

  private BigDecimal parseDecimal(String raw) {
    return new BigDecimal(raw.replace(",", "").trim());
  }
}
