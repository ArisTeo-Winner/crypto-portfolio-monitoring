package com.mx.cryptomonitor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.*; // 🔥 Importa anyString() y eq()

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.mx.cryptomonitor.infrastructure.api.MarketDataService;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {
	
    private static final Logger logger = LoggerFactory.getLogger(MarketDataServiceTest.class);

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MarketDataService marketDataService;

    private final String STOCK_SYMBOL = "IBM";
    private final LocalDate historicalDate = LocalDate.of(2024, 9, 23);


    @BeforeEach
    void setUp() {
        // 🔥 Configurar manualmente las propiedades
        ReflectionTestUtils.setField(marketDataService, "alphaVantageBaseUrl", "https://www.alphavantage.co");
        ReflectionTestUtils.setField(marketDataService, "alphaVantageApiKey", "2B9V32C85SDUIX5Y");
         
    }
 
    @Test
    void testGetStockPrice() {
        logger.info("=== Ejecutando método testGetStockPrice() desde MarketDataServiceTest ===");

        // 🔥 Simulación con estructura compatible
        Map<String, Object> timeSeries = new LinkedHashMap<>();
        timeSeries.put("2025-02-10", new LinkedHashMap<>(Map.of("4. close", "233.1400")));
        timeSeries.put("2025-02-07", new LinkedHashMap<>(Map.of("4. close", "229.1500")));

        Map<String, Object> mockResponse = new LinkedHashMap<>();
        mockResponse.put("Time Series (Daily)", timeSeries);

        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockResponse);

        Optional<BigDecimal> price = marketDataService.getStockPrice(STOCK_SYMBOL);

        logger.info("=== StockPrice() === " + price);

        assertTrue(price.isPresent(), "El precio no debe ser vacío");
        assertEquals(new BigDecimal("233.1400"), price.get());
    }
   
    @Test
    void testGetHistoricalStockPriceSuccess() {
        logger.info("=== Ejecutando testGetHistoricalStockPriceSuccess() desde MarketDataServiceTest ===");

        // Simula la estructura de la respuesta de Alpha Vantage
        Map<String, Object> dailyData = new LinkedHashMap<>();
        dailyData.put("4. close", "233.1400");

        Map<String, Object> timeSeries = new LinkedHashMap<>();
        timeSeries.put(historicalDate.toString(), dailyData); // clave "2025-02-10"

        Map<String, Object> mockResponse = new LinkedHashMap<>();
        mockResponse.put("Time Series (Daily)", timeSeries);

        // Simula la respuesta de RestTemplate
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockResponse);

        Optional<BigDecimal> priceOpt = marketDataService.getHistoricalStockPrice(STOCK_SYMBOL, historicalDate);
        
        logger.info("=== HistoricalStockPrice() === :" + priceOpt);

        
        assertTrue(priceOpt.isPresent(), "El precio histórico no debe estar vacío");
        assertEquals(new BigDecimal("233.1400"), priceOpt.get());
    }/*
    */
    
    /*
    @Test
    void testGetHistoricalStockDataAsJsonSuccess() {
        logger.info("=== Ejecutando testGetHistoricalStockDataAsJsonSuccess() desde MarketDataServiceTest===");

        // Simula la estructura de la respuesta de Alpha Vantage para la fecha 2024-11-15
        Map<String, Object> dailyData = new LinkedHashMap<>();
        dailyData.put("1. open", "218.0000");
        dailyData.put("2. high", "220.6200");
        dailyData.put("3. low", "217.2700");
        dailyData.put("4. close", "220.5000");
        dailyData.put("5. volume", "4074755");

        Map<String, Object> timeSeries = new LinkedHashMap<>();
        String fechaDeseada = historicalDate.toString();  // "2024-11-15"
        timeSeries.put(fechaDeseada, dailyData);

        Map<String, Object> mockResponse = new LinkedHashMap<>();
        mockResponse.put("Time Series (Daily)", timeSeries);

        // Simula la respuesta de RestTemplate
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockResponse);

        // Invoca el método que devuelve el JSON filtrado
        Optional<String> jsonDataOpt = marketDataService.getHistoricalStockDataAsJson(STOCK_SYMBOL, historicalDate);

        assertTrue(jsonDataOpt.isPresent(), "Debe haber datos históricos en formato JSON");
        String jsonData = jsonDataOpt.get();
        logger.info("JSON formateado:\n{}", jsonData);

        // Verifica la estructura del JSON utilizando org.json.JSONObject
        JSONObject jsonObject = null;
		try {
			jsonObject = new JSONObject(jsonData);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        assertTrue(jsonObject.has(fechaDeseada), "El objeto JSON debe tener la clave de la fecha " + fechaDeseada);

        JSONObject dailyDataJson = null;
		try {
			dailyDataJson = jsonObject.getJSONObject(fechaDeseada);
			
			logger.info("JSONObject formateado:\n{}",dailyDataJson.toString(4));
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		
        try {
			assertEquals("218.0000", dailyDataJson.getString("1. open"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("220.6200", dailyDataJson.getString("2. high"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("217.2700", dailyDataJson.getString("3. low"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("220.5000", dailyDataJson.getString("4. close"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("4074755", dailyDataJson.getString("5. volume"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }*/
}
