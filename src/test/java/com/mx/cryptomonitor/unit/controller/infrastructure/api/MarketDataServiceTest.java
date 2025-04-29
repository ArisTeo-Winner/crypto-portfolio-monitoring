package com.mx.cryptomonitor.unit.controller.infrastructure.api;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.*; // 🔥 Importa anyString() y eq()


import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    private HttpClient httpClient;

    @Mock
    private RestTemplate restTemplate;

    
    private MarketDataService marketDataService;

    private final String CRYPTO_SYMBOL = "BTC";
    private final LocalDate HISTORICAL_DATE = LocalDate.of(2024, 1, 20);
    
    private final String STOCK_SYMBOL = "AMZN";


    @BeforeEach
    void setUp() {

        marketDataService = new MarketDataService(restTemplate);
    }
    
    
    /*  
    @Test
    public void testGetPrice_Success() throws Exception {
        // Simular respuesta exitosa
        HttpResponse<Object> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"data\":{\"BTC\":{\"quote\":{\"USD\":{\"price\":86203.82}}}}}");
        when(httpClient.send(any(HttpRequest.class), any())).thenReturn(mockResponse);

        // Ejecutar y verificar
        Optional<BigDecimal> price = marketDataService.getCryptoPrice(CRYPTO_SYMBOL);

        assertEquals(86203.82, price, 0.001);
    }

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



    
   */
    /*
    @Test
    void testGetCryptoPrice_Success() {
    	
        logger.info("=== Ejecutando método testGetCryptoPrice_Success() desde MarketDataServiceTest ===");
    	logger.info("Criptomonedas :"+CRYPTO_SYMBOL);

    	
        Map<String, Object> mockResponse = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> symbolData = new HashMap<>();
        Map<String, Object> quote = new HashMap<>();
        Map<String, Object> usdData = new HashMap<>();
        usdData.put("price", "45000.75");
        quote.put("USD", usdData);
        symbolData.put("quote", quote);
        data.put(CRYPTO_SYMBOL, symbolData);
        mockResponse.put("data", data);

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(mockResponse));

        Optional<BigDecimal> price = marketDataService.getCryptoPrice(CRYPTO_SYMBOL);
        
        
		logger.info("Valor de"+CRYPTO_SYMBOL+" = ",price);

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
    /**/

    private void assertEquals(double expected, Optional<BigDecimal> price, double delta) {
		// TODO Auto-generated method stub
		
	}




	@Test
    void testGetStockPrice_withMockedApiResponse() {
        // Simular respuesta de la API (estructura anidada)
        Map<String, Object> timeSeries = new LinkedHashMap<>();
        Map<String, String> dayData = new LinkedHashMap<>();
        dayData.put("4. close", "172.35");
        timeSeries.put("2025-04-05", dayData);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("Time Series (Daily)", timeSeries);

        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        // Ejecutar método
        Optional<BigDecimal> stockPrice = marketDataService.getStockPrice("AAPL");

        BigDecimal price = stockPrice.orElseThrow(() ->
			new RuntimeException("No se pudo obtener el precio actual del activo"));

        // Validar
        Assert.assertEquals(new BigDecimal("172.35"), price);
    }
   
}
