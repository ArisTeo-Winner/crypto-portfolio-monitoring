package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.service.BmvHistoryService;
import com.mx.cryptomonitor.marketdata.application.service.HybridQuoteService;
import com.mx.cryptomonitor.marketdata.domain.model.BmvHistoricalPoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/marketdata")
@RequiredArgsConstructor
public class BmvDataController {

  private final BmvHistoryService bmvHistoryService;
  private final HybridQuoteService hybridQuoteService;

  @Operation(
      summary = "Precios historicos BMV",
      description =
          "Obtiene el historico de precios de cierre de una emisora en la BMV, usando cache"
              + " incremental respaldada por DataBursatil.")
  @ApiResponse(responseCode = "200", description = "Historico obtenido correctamente")
  @ApiResponse(responseCode = "502", description = "Error en el proveedor DataBursatil")
  @GetMapping("/bmv/historical/{symbol}")
  public ResponseEntity<List<BmvHistoricalPoint>> getHistorical(
      @PathVariable("symbol") String symbol,
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(bmvHistoryService.getHistory(symbol, from, to));
  }

  @Operation(
      summary = "Tipo de cambio USD/MXN",
      description = "Obtiene el tipo de cambio USD/MXN actual reportado por DataBursatil.")
  @ApiResponse(responseCode = "200", description = "Tipo de cambio obtenido correctamente")
  @ApiResponse(responseCode = "502", description = "Error en el proveedor DataBursatil")
  @GetMapping("/fx/usdmxn")
  public ResponseEntity<BigDecimal> getUsdMxnRate() {
    return ResponseEntity.ok(hybridQuoteService.getUsdMxnRate());
  }
}
