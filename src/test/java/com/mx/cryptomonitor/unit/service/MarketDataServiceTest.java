package com.mx.cryptomonitor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.*; // 🔥 Importa anyString() y eq()

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.aspectj.weaver.loadtime.Options;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;

import net.bytebuddy.asm.Advice.Argument;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {
	
    private static final Logger logger = LoggerFactory.getLogger(MarketDataServiceTest.class);

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MarketDataService marketDataService;

    private final String STOCK_SYMBOL = "IBM";
    private final LocalDate historicalDate = LocalDate.of(2024, 9, 23);


    private String mockJsonHistorical = "";
    
    @BeforeEach
    void setUp() {
        // 🔥 Configurar manualmente las propiedades
        ReflectionTestUtils.setField(marketDataService, "alphaVantageBaseUrl", "https://www.alphavantage.co");
        ReflectionTestUtils.setField(marketDataService, "alphaVantageApiKey", "2B9V32C85SDUIX5Y");
         
        mockJsonHistorical = """
        		{
        		    "Meta Data": {
        		        "1. Information": "Daily Prices (open, high, low, close) and Volumes",
        		        "2. Symbol": "AMZN",
        		        "3. Last Refreshed": "2025-04-21",
        		        "4. Output Size": "Compact",
        		        "5. Time Zone": "US/Eastern"
        		    },
        		                "Time Series (Daily)": {                
        		        		    
        		        "2025-04-21": {
        		            "1. open": "169.6000",
        		            "2. high": "169.6000",
        		            "3. low": "165.2850",
        		            "4. close": "167.3200",
        		            "5. volume": "48126111"
        		        },
        		        "2025-04-17": {
        		            "1. open": "176.0000",
        		            "2. high": "176.2100",
        		            "3. low": "172.0000",
        		            "4. close": "172.6100",
        		            "5. volume": "44726453"
        		        },
        		        "2025-04-16": {
        		            "1. open": "176.2900",
        		            "2. high": "179.1046",
        		            "3. low": "171.4100",
        		            "4. close": "174.3300",
        		            "5. volume": "51875316"
        		        },
        		        "2025-04-15": {
        		            "1. open": "181.4100",
        		            "2. high": "182.3500",
        		            "3. low": "177.9331",
        		            "4. close": "179.5900",
        		            "5. volume": "43641952"
        		        }

        		                }
        		            }
        		            """;
    }
    
    @Test    
    public void testGetMarketData() {
    	
    	logger.info("=== Executing test: testGetMarketData");    	    	
    	
    	Map<String, String> quetaData = new HashMap<>();
    	quetaData.put("01. symbol", "AAPL");
    	quetaData.put("02. open", "207.67");
    	quetaData.put("05. price", "206.86");
    	quetaData.put("06. volume", "40912064");
    	
    	Map<String, Object> globalQuote = new HashMap<>();
    	globalQuote.put("Global Quote", quetaData);
    	
    	when(restTemplate.getForObject(ArgumentMatchers.contains("symbol=AAPL"), eq(Map.class))).thenReturn(globalQuote);
    	
    	Optional<BigDecimal> price = marketDataService.getStockQuote("AAPL");
    	
    	BigDecimal currentPrice = price.get();
    	
    	logger.info("Mapeo de actualResponse. \"05. price\": {}", price.get());
    	    	
    	assertNotNull(price);
    	assertEquals(new BigDecimal("206.86"), currentPrice);    	        	    	
    }
    
    @Test
    void getPriceForDate_validDate_returnsPrice() throws JsonProcessingException, Exception{
    	logger.info("=== Executing test: getPriceForDate_validDate_returnsPrice");
    	
     	String symbol = "AMZN";
     	
     	LocalDate date = LocalDate.of(2025, 04, 16);    	    
      
     	logger.info("Consultando precio para {} en fecha {}", symbol, date);
     	
        //ResponseEntity<String> mockResponse = new ResponseEntity<>(mockJson, HttpStatus.OK);
        

        Map<String, Object> mockResponse = new ObjectMapper().readValue(mockJsonHistorical, new TypeReference<Map<String, Object>>() {});
	        
     	
    	when(restTemplate.getForObject(contains("symbol=AMZN"), eq(Map.class))).thenReturn(mockResponse);
    	
    	Optional<BigDecimal> price = marketDataService.getClosePriceForDate(symbol, date);
    	
    	logger.info("Mapeo de price por fecha. Fecha:{}, \"05. price\": {}",date, price.get());
    	
    	assertTrue(price.isPresent());
    	//assertEquals(new BigDecimal("238.2600"),price.get());
    	//assertEquals(date,);
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
    void getDailyPrice_invalidDate_returnsEmpty() throws JsonProcessingException, Exception {
         String symbol = "AAPL";
        LocalDate date = LocalDate.of(2024, 2, 8);

        String mockApiResponse = """
            {                "Meta Data": {
                    "1. Information": "Daily Prices (open, high, low, close) and Volumes",
                    "2. Symbol": "AAPL",
                    "3. Last Refreshed": "2025-05-20",
                    "4. Output Size": "Compact",
                    "5. Time Zone": "US/Eastern"
                },
              "Time Series (Daily)": {
                "2024-01-09": {
                  "1. open": "150.00",
                  "2. high": "152.00",
                  "3. low": "149.00",
                  "4. close": "151.00",
                  "5. volume": "1000000"
                },
                "2024-01-10": {
                  "1. open": "152.00",
                  "2. high": "153.00",
                  "3. low": "150.00",
                  "4. close": "152.50",
                  "5. volume": "1200000"
                }
              }
            }
            """;

        //ResponseEntity<String> mockResponse = new ResponseEntity<>(mockApiResponse, HttpStatus.OK);
        
        Map<String, Object> mockResponse = new ObjectMapper().readValue(mockApiResponse, new TypeReference<Map<String, Object>>() {});

     	
    	when(restTemplate.getForObject(contains("symbol=AAPL"), eq(Map.class))).thenReturn(mockResponse);
    	
    	Optional<BigDecimal> price = marketDataService.getClosePriceForDate(symbol, date);

        assertEquals(Optional.empty(), price);
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
