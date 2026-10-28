package com.mx.cryptomonitor.statementimport.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deriva llaves de idempotencia deterministicas por fila para reusar {@code
 * TransactionIdempotencyService} sin necesidad de un header de idempotencia por documento: subir el
 * mismo PDF dos veces produce las mismas llaves y por lo tanto cero transacciones nuevas.
 */
final class StatementIdempotencyKeys {

  private StatementIdempotencyKeys() {}

  static String sha256(String... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String part : parts) {
        digest.update(part.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '|');
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 no esta disponible en la JVM", ex);
    }
  }
}
