package com.mx.cryptomonitor.unit.controller.infrastructure.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.*; // 🔥 Importa anyString() y eq()


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.mx.cryptomonitor.infrastructure.api.MarketDataService;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    private final Logger logger = LoggerFactory.getLogger(MarketDataServiceTest.class);


    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MarketDataService marketDataService;

    private final String SYMBOL = "BTC";
    private final LocalDate HISTORICAL_DATE = LocalDate.of(2024, 1, 20);
    
    private final String STOCK_SYMBOL = "AMZN";


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
/*
    @Test
    void testGetHistoricalPrice_Success() {
        // 📌 Simular respuesta JSON de Alpha Vantage
        Map<String, Object> mockResponse = new HashMap<>();
        Map<String, Object> timeSeries = new HashMap<>();
        Map<String, Object> historicalData = new HashMap<>();
        historicalData.put("4. close", "42000.50"); // Simular precio de cierre
        timeSeries.put(HISTORICAL_DATE.toString(), historicalData);
        mockResponse.put("Time Series (Daily)", timeSeries);

        // 📌 Simular la respuesta del API
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(mockResponse));

        // 📌 Ejecutar el método
        BigDecimal price = marketDataService.getHistoricalPrice(SYMBOL, HISTORICAL_DATE);

        System.out.println("Resultado esperado = "+price);
        // 📌 Verificar resultado esperado
        assertNotNull(price, "El precio no debe ser null");
        assertEquals(new BigDecimal("42000.50"), price, "El precio no coincide con la simulación");
    }
    
    @Test
    void testGetHistoricalPrice_NotFound() {
        // 📌 Simular respuesta de Alpha Vantage sin datos históricos
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("Time Series (Daily)", new HashMap<>()); // Simulación de datos vacíos

        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(mockResponse));

        // 📌 Validar que se lanza una excepción
        Exception exception = assertThrows(RuntimeException.class, () -> {
            marketDataService.getHistoricalPrice(SYMBOL, HISTORICAL_DATE);
        });

        // 📌 Verificar que el mensaje de la excepción es correcto
        String expectedMessage = "❌ No se encontró precio para";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage), "El mensaje de error no coincide");
    }



    @Test
    void testGetCryptoPrice_Success() {
        Map<String, Object> mockResponse = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> symbolData = new HashMap<>();
        Map<String, Object> quote = new HashMap<>();
        Map<String, Object> usdData = new HashMap<>();
        usdData.put("price", "45000.75");
        quote.put("USD", usdData);
        symbolData.put("quote", quote);
        data.put(SYMBOL, symbolData);
        mockResponse.put("data", data);

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(mockResponse));

        BigDecimal price = marketDataService.getCryptoPrice(SYMBOL);

        assertNotNull(price);
        assertEquals(new BigDecimal("45000.75"), price);
    }



    @Test
    void testGetCryptoPrice_Fail() {
        Map<String, Object> mockResponse = new HashMap<>();

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(mockResponse));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            marketDataService.getCryptoPrice(SYMBOL);
        });

        assertTrue(exception.getMessage().contains("❌ Error al extraer el precio"));
    }
    */
    @Test
    void testGetStockPrice() {
    	
		logger.info("=== Ejecutando método testGetStockPrice() desde MarketDataServiceTest ===");

        // Simulación de respuesta JSON de Alpha Vantage
        Map<String, Object> mockResponse = Map.of(
            "Time Series (Daily)", Map.of(
                "2025-02-10", Map.of("4. close", "233.1400"),
                "2025-02-07", Map.of("4. close", "229.1500")
            )
        );

        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockResponse);

        Optional<BigDecimal> price = marketDataService.getStockPrice(STOCK_SYMBOL);
        
		logger.info("=== Price ==="+price);


        assertTrue(price.isPresent(), "El precio no debe ser vacío");
        assertEquals(new BigDecimal("233.1400"), price.get());
    }
}
