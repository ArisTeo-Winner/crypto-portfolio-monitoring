package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.dto.response.StockTimeSeriesResponse;
import com.mx.cryptomonitor.marketdata.application.service.StockDataService;
import com.mx.cryptomonitor.marketdata.domain.model.StockInterval;
import com.mx.cryptomonitor.marketdata.domain.model.StockTimeSeries;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/marketdata")
@RequiredArgsConstructor
public class StockDataController {

  private final StockDataService stockDataService;

  // -------------------------------------------------------------------------
  // Precio en tiempo real (Previous Close)
  // -------------------------------------------------------------------------

  @Operation(
      summary = "Precio en tiempo real (Previous Close)",
      description =
          "Obtiene el último precio disponible de un stock consultando los proveedores"
              + " configurados en orden: Twelve Data → Alpha Vantage → Polygon.")
  @ApiResponse(responseCode = "200", description = "Precio obtenido correctamente")
  @ApiResponse(responseCode = "400", description = "Symbol vacío o nulo")
  @ApiResponse(responseCode = "404", description = "Sin datos para el symbol")
  @ApiResponse(responseCode = "502", description = "Error en todos los proveedores")
  @GetMapping("/stock")
  public ResponseEntity<String> getStockPrice(@RequestParam("symbol") String symbol) {
    if (symbol == null || symbol.trim().isEmpty()) {
      return ResponseEntity.badRequest().body("El simbolo de la accion no debe ser nulo ni vacio.");
    }

    Optional<BigDecimal> priceOpt = stockDataService.getStockQuote(symbol);
    return priceOpt
        .map(price -> ResponseEntity.ok(price.toPlainString()))
        .orElseGet(
            () ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontro informacion para el simbolo: " + symbol));
  }

  // -------------------------------------------------------------------------
  // Precio histórico de cierre (un punto por fecha)
  // -------------------------------------------------------------------------

  @GetMapping("/stock/historical/{symbol}/{date}")
  @Operation(
      summary = "Precio histórico de cierre",
      description = "Obtiene el precio de cierre para un ticker y una fecha ISO yyyy-MM-dd.")
  @ApiResponse(responseCode = "200", description = "Precio historico obtenido correctamente")
  @ApiResponse(responseCode = "400", description = "Parametros invalidos")
  @ApiResponse(responseCode = "404", description = "Sin datos para esa fecha")
  public ResponseEntity<String> getHistoricalStockPrice(
      @PathVariable("symbol") String symbol, @PathVariable("date") String date) {

    if (symbol == null || symbol.trim().isEmpty()) {
      return ResponseEntity.badRequest().body("El simbolo de la accion no debe ser nulo ni vacio.");
    }
    if (date == null || date.trim().isEmpty()) {
      return ResponseEntity.badRequest().body("La fecha no debe ser nula ni vacia.");
    }

    LocalDate historicalDate;
    try {
      historicalDate = LocalDate.parse(date);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("La fecha debe tener el formato yyyy-MM-dd.");
    }

    Optional<BigDecimal> priceOpt =
        stockDataService.getHistoricalStockPrice(symbol, historicalDate);
    return priceOpt
        .map(price -> ResponseEntity.ok(price.toPlainString()))
        .orElseGet(
            () ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        "No se encontro informacion historica para el simbolo: "
                            + symbol
                            + " en la fecha: "
                            + date));
  }

  // -------------------------------------------------------------------------
  // Serie temporal OHLCV con intervalo configurable (Twelve Data)
  // -------------------------------------------------------------------------

  @GetMapping("/stock/time-series")
  @Operation(
      summary = "Serie temporal OHLCV con intervalo configurable",
      description =
          "Obtiene una serie de velas OHLCV para el symbol e interval solicitados usando"
              + " Twelve Data. Intervalos válidos: "
              + "1min, 5min, 15min, 30min, 45min, 1h, 2h, 4h, 8h, 1day, 1week, 1month."
              + " outputSize controla el número de puntos retornados (1–5000, default 30).")
  @ApiResponse(responseCode = "200", description = "Serie obtenida correctamente")
  @ApiResponse(responseCode = "400", description = "Symbol o interval inválidos")
  @ApiResponse(responseCode = "404", description = "Symbol no encontrado en el proveedor")
  @ApiResponse(
      responseCode = "503",
      description = "Proveedor no disponible (TWELVEDATA_API_KEY no configurada)")
  @ApiResponse(responseCode = "502", description = "Error en el proveedor externo")
  public ResponseEntity<?> getTimeSeries(
      @RequestParam("symbol") String symbol,
      @RequestParam("interval") String interval,
      @RequestParam(value = "outputSize", defaultValue = "30") int outputSize) {

    // Validar symbol
    if (symbol == null || symbol.trim().isEmpty()) {
      return ResponseEntity.badRequest()
          .body(problemDetail(400, "El simbolo no debe ser nulo ni vacio."));
    }

    // Validar interval contra el enum
    Optional<StockInterval> stockInterval = StockInterval.fromCode(interval);
    if (stockInterval.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(
              problemDetail(
                  400,
                  "Interval invalido: '"
                      + interval
                      + "'. Valores permitidos: "
                      + StockInterval.validCodes()));
    }

    // Validar outputSize
    if (outputSize < 1 || outputSize > 5000) {
      return ResponseEntity.badRequest()
          .body(problemDetail(400, "outputSize debe estar entre 1 y 5000."));
    }

    // Verificar que el proveedor esté disponible
    Optional<StockTimeSeries> result =
        stockDataService.getTimeSeries(symbol.trim(), stockInterval.get(), outputSize);

    if (result.isEmpty()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
              problemDetail(
                  503,
                  "El proveedor de series temporales no esta disponible."
                      + " Configure la variable de entorno TWELVEDATA_API_KEY."));
    }

    return ResponseEntity.ok(StockTimeSeriesResponse.from(result.get()));
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  private ProblemDetail problemDetail(int status, String detail) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setDetail(detail);
    return pd;
  }
}
