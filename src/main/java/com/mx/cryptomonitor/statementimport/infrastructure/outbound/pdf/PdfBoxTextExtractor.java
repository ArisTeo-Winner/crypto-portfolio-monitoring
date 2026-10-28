package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;

/**
 * Extrae texto de un PDF, tolerando el prefijo de cola de impresion (~100 bytes {@code ^{doc_title
 * = ...}}) que el portal de GBM antepone al marcador real {@code %PDF-1.x} en los estados de cuenta
 * descargados.
 */
@Component
public class PdfBoxTextExtractor {

  private static final int HEADER_SEARCH_WINDOW = 1024;
  private static final byte[] PDF_MARKER = "%PDF".getBytes(StandardCharsets.US_ASCII);

  public List<String> extractPagesText(byte[] rawBytes) {
    byte[] pdfBytes = stripLeadingNonPdfBytes(rawBytes);
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      List<String> pages = new ArrayList<>(document.getNumberOfPages());
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      for (int page = 1; page <= document.getNumberOfPages(); page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        pages.add(stripper.getText(document));
      }
      return pages;
    } catch (IOException ex) {
      throw new InvalidStatementDocumentException("No se pudo leer el archivo PDF", ex);
    }
  }

  private byte[] stripLeadingNonPdfBytes(byte[] rawBytes) {
    if (rawBytes.length < PDF_MARKER.length) {
      throw new InvalidStatementDocumentException(
          "El archivo no contiene un encabezado PDF valido");
    }
    int searchLimit = Math.min(HEADER_SEARCH_WINDOW, rawBytes.length - PDF_MARKER.length);
    for (int offset = 0; offset <= searchLimit; offset++) {
      if (matchesAt(rawBytes, offset)) {
        return offset == 0 ? rawBytes : Arrays.copyOfRange(rawBytes, offset, rawBytes.length);
      }
    }
    throw new InvalidStatementDocumentException("El archivo no contiene un encabezado PDF valido");
  }

  private boolean matchesAt(byte[] data, int offset) {
    for (int i = 0; i < PDF_MARKER.length; i++) {
      if (data[offset + i] != PDF_MARKER[i]) {
        return false;
      }
    }
    return true;
  }
}
