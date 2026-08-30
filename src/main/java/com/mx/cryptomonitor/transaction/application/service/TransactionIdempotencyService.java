package com.mx.cryptomonitor.transaction.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.domain.exception.IdempotencyConflictException;
import com.mx.cryptomonitor.transaction.domain.exception.InvalidTransactionException;
import com.mx.cryptomonitor.transaction.domain.model.TransactionIdempotencyRecord;
import com.mx.cryptomonitor.transaction.domain.model.TransactionIdempotencyStatus;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionIdempotencyRecordRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionIdempotencyService {

  private static final int MAX_KEY_LENGTH = 200;

  private final TransactionIdempotencyRecordRepository idempotencyRecordRepository;
  private final TransactionRepository transactionRepository;
  private final TransactionMapper transactionMapper;
  private final ObjectMapper objectMapper;

  @Value("${transaction.idempotency.ttl:7d}")
  private Duration ttl;

  @Transactional
  public TransactionResponse executeForTransactionResponse(
      UUID userId,
      String operationScope,
      String idempotencyKey,
      Object requestPayload,
      Supplier<TransactionResponse> action) {
    TransactionIdempotencyRecord record =
        claimRecord(userId, operationScope, idempotencyKey, requestPayload, true);
    if (record.getStatus() == TransactionIdempotencyStatus.COMPLETED) {
      return resolveStoredResponse(userId, record);
    }

    TransactionResponse response = action.get();
    record.setStatus(TransactionIdempotencyStatus.COMPLETED);
    record.setResultTransactionId(response.transactionId());
    record.setCompletedAt(OffsetDateTime.now());
    idempotencyRecordRepository.save(record);
    return response;
  }

  @Transactional
  public void executeForVoid(
      UUID userId,
      String operationScope,
      String idempotencyKey,
      Object requestPayload,
      Runnable action) {
    TransactionIdempotencyRecord record =
        claimRecord(userId, operationScope, idempotencyKey, requestPayload, false);
    if (record.getStatus() == TransactionIdempotencyStatus.COMPLETED) {
      return;
    }

    action.run();
    record.setStatus(TransactionIdempotencyStatus.COMPLETED);
    record.setCompletedAt(OffsetDateTime.now());
    idempotencyRecordRepository.save(record);
  }

  /**
   * Olvida el registro de idempotencia que produjo una transacción, para que al eliminarla se pueda
   * volver a importar/registrar el mismo documento (misma clave) sin chocar con el registro
   * huérfano.
   */
  @Transactional
  public void forgetByResultTransactionId(UUID transactionId) {
    idempotencyRecordRepository.deleteByResultTransactionId(transactionId);
  }

  private TransactionIdempotencyRecord claimRecord(
      UUID userId,
      String operationScope,
      String rawIdempotencyKey,
      Object requestPayload,
      boolean requiresStoredTransaction) {
    String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
    String requestHash = computeRequestHash(operationScope, requestPayload);
    OffsetDateTime now = OffsetDateTime.now();

    return idempotencyRecordRepository
        .findByUserIdAndOperationScopeAndIdempotencyKey(userId, operationScope, idempotencyKey)
        .map(
            record ->
                handleExistingRecord(userId, record, requestHash, now, requiresStoredTransaction))
        .orElseGet(
            () ->
                persistNewClaim(
                    userId,
                    operationScope,
                    idempotencyKey,
                    requestHash,
                    now,
                    requiresStoredTransaction));
  }

  private TransactionIdempotencyRecord handleExistingRecord(
      UUID userId,
      TransactionIdempotencyRecord record,
      String requestHash,
      OffsetDateTime now,
      boolean requiresStoredTransaction) {
    if (record.getExpiresAt() != null && !record.getExpiresAt().isAfter(now)) {
      idempotencyRecordRepository.delete(record);
      idempotencyRecordRepository.flush();
      return persistNewClaim(
          userId,
          record.getOperationScope(),
          record.getIdempotencyKey(),
          requestHash,
          now,
          requiresStoredTransaction);
    }

    if (!record.getRequestHash().equals(requestHash)) {
      throw new IdempotencyConflictException(
          "La clave de idempotencia ya fue usada con un payload diferente");
    }

    if (record.getStatus() == TransactionIdempotencyStatus.IN_PROGRESS) {
      throw new IdempotencyConflictException(
          "Ya existe una solicitud en progreso con la misma clave de idempotencia");
    }

    if (requiresStoredTransaction && record.getResultTransactionId() == null) {
      throw new IdempotencyConflictException(
          "La clave de idempotencia ya fue consumida por una operacion sin respuesta reutilizable");
    }

    return record;
  }

  private TransactionIdempotencyRecord persistNewClaim(
      UUID userId,
      String operationScope,
      String idempotencyKey,
      String requestHash,
      OffsetDateTime now,
      boolean requiresStoredTransaction) {
    TransactionIdempotencyRecord record =
        TransactionIdempotencyRecord.builder()
            .userId(userId)
            .operationScope(operationScope)
            .idempotencyKey(idempotencyKey)
            .requestHash(requestHash)
            .status(TransactionIdempotencyStatus.IN_PROGRESS)
            .createdAt(now)
            .expiresAt(now.plus(ttl))
            .build();

    try {
      return idempotencyRecordRepository.saveAndFlush(record);
    } catch (DataIntegrityViolationException ex) {
      TransactionIdempotencyRecord existingRecord =
          idempotencyRecordRepository
              .findByUserIdAndOperationScopeAndIdempotencyKey(
                  userId, operationScope, idempotencyKey)
              .orElseThrow(
                  () ->
                      new IdempotencyConflictException(
                          "No se pudo resolver la clave de idempotencia duplicada"));
      return handleExistingRecord(
          userId, existingRecord, requestHash, now, requiresStoredTransaction);
    }
  }

  private TransactionResponse resolveStoredResponse(
      UUID userId, TransactionIdempotencyRecord completedRecord) {
    UUID transactionId = completedRecord.getResultTransactionId();
    return transactionRepository
        .findByTransactionIdAndUserId(transactionId, userId)
        .map(transactionMapper::toResponse)
        .orElseThrow(
            () ->
                new IdempotencyConflictException(
                    "La respuesta original ya no esta disponible para esta clave de idempotencia"));
  }

  private String normalizeIdempotencyKey(String rawIdempotencyKey) {
    if (rawIdempotencyKey == null || rawIdempotencyKey.isBlank()) {
      throw new InvalidTransactionException("El header X-Idempotency-Key es obligatorio");
    }
    String normalized = rawIdempotencyKey.trim();
    if (normalized.length() > MAX_KEY_LENGTH) {
      throw new InvalidTransactionException(
          "El header X-Idempotency-Key excede la longitud maxima");
    }
    return normalized;
  }

  private String computeRequestHash(String operationScope, Object requestPayload) {
    try {
      byte[] payloadBytes = objectMapper.writeValueAsBytes(requestPayload);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(operationScope.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
      return HexFormat.of().formatHex(digest.digest(payloadBytes));
    } catch (JsonProcessingException ex) {
      throw new InvalidTransactionException(
          "No se pudo serializar la solicitud para calcular la idempotencia", ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 no esta disponible en la JVM", ex);
    }
  }
}
