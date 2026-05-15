package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.marketdata.application.service.StockDataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/v1/marketdata")
public class StockDataController {

  private static final Logger logger = LoggerFactory.getLogger(StockDataController.class);

  public final StockDataService stockDataService;

  public StockDataController(StockDataService stockDataService) {
    this.stockDataService = stockDataService;
  }

  @Operation(
      summary = "Precio de cierre anterior (Previous Close)",
      description =
          "Obtiene el precio de cierre anterior de un Stock usando Alphavantage `query?function=GLOBAL_QUOTE&symbol={symbol}&apikey={apikey}`.")
  @ApiResponse(responseCode = "200", description = "Precio obtenido correctamente")
  @ApiResponse(responseCode = "400", description = "Ticker invalido")
  @ApiResponse(responseCode = "404", description = "No se encontro informacion")
  @ApiResponse(responseCode = "502", description = "Error de comunicacion con Alphavantage")
  @ApiResponse(responseCode = "500", description = "Error interno")
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

  @GetMapping("/stock/historical/{symbol}/{date}")
  @Operation(
      summary = "Precio historico de cierre",
      description = "Obtiene el precio de cierre para un ticker y una fecha ISO yyyy-MM-dd")
  @ApiResponse(responseCode = "200", description = "Precio historico obtenido correctamente")
  @ApiResponse(responseCode = "400", description = "Parametros invalidos")
  @ApiResponse(responseCode = "404", description = "No se encontro informacion historica")
  @ApiResponse(responseCode = "500", description = "Error interno")
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
}
