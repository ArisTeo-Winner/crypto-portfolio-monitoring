package com.mx.cryptomonitor.integration;


import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;

@SpringBootTest
class MarketDataServiceIntegrationTest {
	
    private final Logger logger = LoggerFactory.getLogger(MarketDataServiceIntegrationTest.class);


    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private RestTemplate restTemplate; // Este bean se crea en el contexto de Spring

    @Autowired
    private ObjectMapper objectMapper;

    private MockRestServiceServer mockServer;

    // Usamos el mismo símbolo y fecha para la prueba.
    private final String STOCK_SYMBOL = "IBM";
    private final LocalDate historicalDate = LocalDate.of(2024, 9, 23);


    @BeforeEach
    void setUp() {
        // Crear un servidor simulado para el RestTemplate
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }
/*
    @Test
    void testGetStockPriceIntegration() throws Exception {
    	
        logger.info("=== Ejecutando método testGetStockPriceIntegration() desde MarketDataServiceIntegrationTest ===");

    	
        // Construir la respuesta simulada en JSON
        Map<String, Object> timeSeries = new LinkedHashMap<>();
        LinkedHashMap<String, Object> dailyData = new LinkedHashMap<>();
        dailyData.put("4. close", "233.1400");
        timeSeries.put("2025-02-10", dailyData);
        timeSeries.put("2025-02-07", new LinkedHashMap<>(Map.of("4. close", "229.1500")));

        Map<String, Object> mockResponseMap = new LinkedHashMap<>();
        mockResponseMap.put("Time Series (Daily)", timeSeries);

        String mockResponseJson = objectMapper.writeValueAsString(mockResponseMap);

        // Construir la URL que se espera que utilice el servicio
        // Se asume que en application.properties están definidos los valores para alphaVantageBaseUrl y alphaVantageApiKey.
        String expectedUrl = marketDataService.getAlphaVantageBaseUrl() + "/query?function=TIME_SERIES_DAILY&symbol=" + STOCK_SYMBOL + "&apikey=" + marketDataService.getAlphaVantageApiKey();
        
        logger.info("=== Construición de la URL :"+expectedUrl);

        
        // Configurar el servidor simulado para interceptar la llamada y devolver la respuesta simulada
        mockServer.expect(requestTo(expectedUrl))
                  .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        Optional<BigDecimal> priceOpt = marketDataService.getStockPrice(STOCK_SYMBOL);

        logger.info("=== Precio :"+priceOpt);

        
        // Verificar que la llamada se realizó y se obtuvo el precio correcto
        assertTrue(priceOpt.isPresent(), "El precio no debe estar vacío");
        assertEquals(new BigDecimal("233.1400"), priceOpt.get(), "El precio debe ser 233.1400");

        // Verificar que el servidor simulado recibió la solicitud
        mockServer.verify();
    }
 */   
    @Test
    public void testGetHistoricalStockDataAsJsonIntegration() {
    	
        logger.info("=== Ejecutando método testGetHistoricalStockDataAsJsonIntegration() desde MarketDataServiceIntegrationTest ===");

    	
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
        
        // Invoca el método que queremos probar
        Optional<String> resultOpt = marketDataService.getHistoricalStockDataAsJson(STOCK_SYMBOL, historicalDate);
        
        assertTrue(resultOpt.isPresent(), "Se debe obtener un JSON con datos históricos");
        String resultJson = resultOpt.get();
        
        // Imprime el JSON resultante (formateado)
        logger.info("JSON formateado:\n" + resultJson);
        
        // Valida la estructura y contenido del JSON utilizando org.json.JSONObject
        JSONObject jsonObject = null;
		try {
			jsonObject = new JSONObject(resultJson);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
        
        // Verifica que se hayan consumido todas las expectativas del servidor simulado
        mockServer.verify();
    }
}
