package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.service.BanxicoCurveService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/marketdata/banxico")
@RequiredArgsConstructor
public class BanxicoDataController {

  private final BanxicoCurveService banxicoCurveService;

  @Operation(
      summary = "Curva de tasas CETES vigente",
      description = "Curva de tasas CETES (plazo en dias -> tasa %) cacheada desde Banxico SIE.")
  @ApiResponse(responseCode = "200", description = "Curva obtenida correctamente")
  @GetMapping("/cetes/curve")
  public ResponseEntity<Map<Integer, BigDecimal>> getCetesCurve() {
    return ResponseEntity.ok(banxicoCurveService.getCurve());
  }
}
