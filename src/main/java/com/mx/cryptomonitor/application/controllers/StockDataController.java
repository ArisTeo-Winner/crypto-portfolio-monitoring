package com.mx.cryptomonitor.application.controllers;

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

import com.mx.cryptomonitor.infrastructure.api.MarketDataService;

@RestController
@RequestMapping("/api/v1/marketdata")
public class StockDataController {

	private static final Logger logger = LoggerFactory.getLogger(StockDataController.class);
	
	public final MarketDataService marketDataService;
	
	public StockDataController(MarketDataService marketDataService) {
		this.marketDataService = marketDataService;
		
	}
	
	/**
	 * Endpoint para obtener el precio de cierre de una acción.
	 * Ejemplo de uso: GET /api/v1/marketdata/stock?symbol=AMZN
	 * 
	 * @param symbol Símbolo de la acción (no nulo ni vacío)
	 * @return ResponseEntity con el precio de cierre o un mensaje de error
	 * 
	 * */
    @GetMapping("/stock")
    public ResponseEntity<?> getStockPrice(@RequestParam("symbol") String symbol) {
        logger.info("Solicitud para obtener precio de acción: {}", symbol);
        
        // Validar que el símbolo no sea nulo ni esté vacío
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("El símbolo de la acción no debe ser nulo ni vacío.");
        }
        
        Optional<BigDecimal> priceOpt = marketDataService.getStockPrice(symbol);
        return priceOpt
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No se encontró información para el símbolo: " + symbol));
    }
    
    /**
     * GET /api/market/stocks/historical/{symbol}/{date}
     * Obtiene el precio histórico de cierre de una acción para la fecha especificada.
     *
     * @param symbol Símbolo de la acción (por ejemplo, "AMZN")
     * @param date   Fecha en formato ISO (yyyy-MM-dd)
     * @return Precio histórico de cierre o un mensaje de error
     */
    @GetMapping("/historical/{symbol}/{date}")
    public ResponseEntity<?> getHistoricalStockPrice(
            @PathVariable("symbol") String symbol,
            @PathVariable("date") String date) {
        logger.info("Solicitud para obtener precio histórico de acción: {} en la fecha: {}", symbol, date);
        // Validar entradas
        if (symbol == null || symbol.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El símbolo de la acción no debe ser nulo ni vacío.");
        }
        if (date == null || date.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La fecha no debe ser nula ni vacía.");
        }
        LocalDate historicalDate;
        try {
            historicalDate = LocalDate.parse(date); // Formato ISO: yyyy-MM-dd
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("La fecha debe tener el formato yyyy-MM-dd.");
        }
        Optional<BigDecimal> priceOpt = marketDataService.getHistoricalStockPrice(symbol, historicalDate);
        return priceOpt
        		.<ResponseEntity<?>>map(price -> ResponseEntity.ok().body(price))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No se encontró información histórica para el símbolo: " + symbol + " en la fecha: " + date));
    }
	
}
