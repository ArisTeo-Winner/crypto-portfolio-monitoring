package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.dto.request.DividendSyncAdminRequest;
import com.mx.cryptomonitor.marketdata.application.dto.request.MergerAdminRequest;
import com.mx.cryptomonitor.marketdata.application.dto.response.DividendSyncResponse;
import com.mx.cryptomonitor.marketdata.application.dto.response.MergerRegisteredResponse;
import com.mx.cryptomonitor.marketdata.application.port.in.RegisterMergerUseCase;
import com.mx.cryptomonitor.marketdata.application.port.in.SyncDividendsUseCase;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Alta administrada de corporate actions sin feed automático (ADR-0015). Solo ADMIN (la ruta {@code
 * /api/v1/admin/corporate-actions/**} se restringe en SecurityConfig). Registra el evento y publica
 * el evento de dominio; el accrual por usuario corre en transaction vía listener (AFTER_COMMIT),
 * por eso la respuesta es 202 (el efecto se materializa al commit).
 */
@RestController
@RequestMapping("/api/v1/admin/corporate-actions")
@RequiredArgsConstructor
public class CorporateActionAdminController {

  private final RegisterMergerUseCase registerMergerUseCase;
  private final SyncDividendsUseCase syncDividendsUseCase;

  @Operation(
      summary = "Registrar una fusión (ADMIN)",
      description =
          "Persiste el evento MERGER + detalle (idempotente) y dispara el accrual del carryover a"
              + " los tenedores. ALL_STOCK usa exchangeRatio; ALL_CASH usa cashPerShare.")
  @PostMapping("/merger")
  public ResponseEntity<MergerRegisteredResponse> registerMerger(
      @Valid @RequestBody MergerAdminRequest request) {
    UUID id =
        registerMergerUseCase.registerMerger(
            request.predecessorSymbol(),
            request.successorSymbol(),
            request.mergerType(),
            request.exchangeRatio(),
            request.cashPerShare(),
            request.currency(),
            request.effectiveDate());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MergerRegisteredResponse(id));
  }

  @Operation(
      summary = "Poblar dividendos on-demand (ADMIN)",
      description =
          "Dispara la ingesta de dividendos de cada símbolo (Massive US → EODHD, que cubre .MX en"
              + " MXN nativo). Idempotente. Usar sufijo de mercado cuando aplique (BIMBOA.MX). El"
              + " accrual por usuario corre en transaction vía listener (AFTER_COMMIT), por eso 202.")
  @PostMapping("/dividends/sync")
  public ResponseEntity<DividendSyncResponse> syncDividends(
      @Valid @RequestBody DividendSyncAdminRequest request) {
    Map<String, Integer> created = new LinkedHashMap<>();
    int totalCreated = 0;
    for (String symbol : request.symbols()) {
      if (!StringUtils.hasText(symbol)) {
        continue;
      }
      int inserted = syncDividendsUseCase.sync(symbol);
      created.put(symbol.trim().toUpperCase(), inserted);
      totalCreated += inserted;
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new DividendSyncResponse(created, totalCreated));
  }
}
