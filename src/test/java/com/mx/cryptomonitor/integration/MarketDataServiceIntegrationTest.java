package com.mx.cryptomonitor.integration;

import static org.mockito.Mockito.*;
import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;



@SpringBootTest
@ActiveProfiles("test")
class MarketDataServiceIntegrationTest {
	
    private final Logger logger = LoggerFactory.getLogger(MarketDataServiceIntegrationTest.class);


    private final String SYMBOL_BTC = "BTC";
    private final String SYMBOL_ETH = "ETH";
    private final String STOCK_SYMBOL = "IBM";


    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private RestTemplate restTemplate; // Este bean se crea en el contexto de Spring

    @Autowired
    private ObjectMapper objectMapper;

    private MockRestServiceServer mockServer;

    // Usamos el mismo símbolo y fecha para la prueba.
    
    private final LocalDate historicalDate = LocalDate.of(2024, 9, 23);
    /*
    @BeforeEach
    void setUp() {
        // Vincula el MockRestServiceServer al RestTemplate
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }*/


    @Test
    void testGetCryptoPriceRealAPI() {
        logger.info("=== Ejecutando método testGetCryptoPriceRealAPI() desde MarketDataServiceIntegrationTest ===");

        Optional<BigDecimal> priceOpt = marketDataService.getCryptoPrice(SYMBOL_BTC);
        
        BigDecimal price = priceOpt.get();
        
        assertNotNull(price);
        logger.info("✅ Último precio de cierre de {}: {}", SYMBOL_BTC, price);

    }
   
    @Test
    void testGetStockPriceIntegration() throws Exception {
    	
        logger.info("=== Ejecutando método testGetStockPriceIntegration() desde MarketDataServiceIntegrationTest ===");

    	
        Optional<BigDecimal> priceOpt = marketDataService.getStockPrice(STOCK_SYMBOL);
        
        BigDecimal price = priceOpt.orElseThrow(() ->
        		new RuntimeException("No se pudo obtener el precio actual del activo"));

        assertNotNull(priceOpt);
        
        //BigDecimal price = priceOpt.get();
        
        logger.info("✅ Último precio de cierre de {}: {}", STOCK_SYMBOL, price);

        
    } /* */
   
    /**/
    @Test
    public void testGetHistoricalStockDataAsJsonIntegration() throws JSONException {
    	
        logger.info("=== Ejecutando método testGetHistoricalStockDataAsJsonIntegration() desde MarketDataServiceIntegrationTest ===");

    	/*
        // Simula la respuesta que Alpha Vantage devolvería para la fecha 2024-09-23
        String expectedApiResponse = "{\n" +
                "  \"Time Series (Daily)\": {\n" +
                "    \"2024-09-23\": {\n" +
                "      \"1. open\": \"218.0000\",\n" +
                "      \"2. high\": \"220.6200\",\n" +
                "      \"3. low\": \"217.2700\",\n" +
                "      \"4. close\": \"220.5000\",\n" +
                "      \"5. volume\": \"4074755\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
        
        // Configura el MockRestServiceServer para esperar una llamada a la URL que contenga el endpoint TIME_SERIES_DAILY
       
        mockServer.expect(ExpectedCount.once(),
                requestTo(containsString("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY")))
                .andRespond(withSuccess(expectedApiResponse, MediaType.APPLICATION_JSON));
         
        mockServer.expect(requestTo(containsString("function=TIME_SERIES_DAILY")))
        	.andRespond(withSuccess("{\"Time Series (Daily)\":{\"2025-04-05\":{\"4. close\":\"172.35\"}}}", MediaType.APPLICATION_JSON));
*/
        // Invoca el método que queremos probar
        Optional<String> resultOpt = marketDataService.getHistoricalStockDataAsJson(STOCK_SYMBOL, historicalDate);
        
        assertTrue(resultOpt.isPresent(), "Se debe obtener un JSON con datos históricos");
        String jsonStr = resultOpt.orElseThrow(() -> 
        		new RuntimeException("No se pudo obtener el precio actual del activo"));
        
        // Imprime el JSON resultante (formateado)
        logger.info("JSON formateado:" + jsonStr);
        
        JSONObject json = new JSONObject(jsonStr);
        assertTrue(json.has(historicalDate.toString()), "El JSON debería contener la fecha especificada");
        JSONObject dailyData = json.getJSONObject(historicalDate.toString());
        assertTrue(dailyData.has("4. close"), "El JSON debería contener el precio de cierre");
        String closePrice = dailyData.getString("4. close");
        BigDecimal price = new BigDecimal(closePrice);
        assertTrue(price.compareTo(BigDecimal.ZERO) > 0, "El precio debería ser positivo");
        
        // Valida la estructura y contenido del JSON utilizando org.json.JSONObject
       /*
        JSONObject jsonObject = null;
		try {
			jsonObject = new JSONObject(resultJson);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        JSONObject jsonObject = new JSONObject(resultJson); // Asegúrate que 'response' no es null

        if (jsonObject == null || !jsonObject.has("Time Series (Daily)")) {
        	logger.warn("⚠ JSON nulo o sin 'Time Series (Daily)'");
        	throw new IllegalArgumentException("Time Series (Daily) failed");
        }

        String dateKey = historicalDate.toString();  // "2024-09-23"
        assertTrue(jsonObject.has(dateKey), "El objeto JSON debe contener la clave de la fecha " + dateKey);
        
        JSONObject dailyData = null;
		try {
			dailyData = jsonObject.getJSONObject(dateKey);
			
			logger.info("dailyData formateado:\n" + dailyData);
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("218.0000", dailyData.getString("1. open"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("220.6200", dailyData.getString("2. high"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("217.2700", dailyData.getString("3. low"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("220.5000", dailyData.getString("4. close"));
			
			logger.info("4. close:" + dailyData.getString("4. close"));

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			assertEquals("4074755", dailyData.getString("5. volume"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        */
        // Verifica que se hayan consumido todas las expectativas del servidor simulado
        mockServer.verify();
    }
}
