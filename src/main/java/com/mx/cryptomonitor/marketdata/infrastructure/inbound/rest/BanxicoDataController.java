package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.dto.response.CetesRateTableEntry;
import com.mx.cryptomonitor.marketdata.application.service.BanxicoCurveService;
import com.mx.cryptomonitor.marketdata.application.service.CetesRateTableService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/marketdata/banxico")
@RequiredArgsConstructor
public class BanxicoDataController {

  private final BanxicoCurveService banxicoCurveService;
  private final CetesRateTableService cetesRateTableService;

  @Operation(
      summary = "Curva de tasas CETES vigente",
      description = "Curva de tasas CETES (plazo en dias -> tasa %) cacheada desde Banxico SIE.")
  @ApiResponse(responseCode = "200", description = "Curva obtenida correctamente")
  @GetMapping("/cetes/curve")
  public ResponseEntity<Map<Integer, BigDecimal>> getCetesCurve() {
    return ResponseEntity.ok(banxicoCurveService.getCurve());
  }

  @Operation(
      summary = "Tabla de tasas CETES",
      description =
          "Plazo, precio (base nominal $10, descuento cupon cero) y tasa vigente de CETES, a"
              + " partir de la curva de Banxico SIE — replica la tabla publica de cetesdirecto."
              + " Limitacion conocida: el precio usa el plazo nominal (28/91/182/364/728 dias),"
              + " no los dias reales al vencimiento del titulo vigente, por lo que puede diferir"
              + " de cetesdirecto en 1-2 centavos (mas en el plazo de 728 dias, que no se subasta"
              + " cada semana). La tasa siempre es exacta.")
  @ApiResponse(responseCode = "200", description = "Tabla obtenida correctamente")
  @GetMapping("/cetes/table")
  public ResponseEntity<List<CetesRateTableEntry>> getCetesTable() {
    return ResponseEntity.ok(cetesRateTableService.getCetesTable());
  }
}
